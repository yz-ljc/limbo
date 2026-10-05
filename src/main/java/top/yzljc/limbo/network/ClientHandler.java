package top.yzljc.limbo.network;

import com.google.gson.JsonObject;
import com.viaversion.viaversion.api.connection.UserConnection;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.handler.codec.CorruptedFrameException;
import top.yzljc.limbo.LimboServer;
import top.yzljc.limbo.protocol.LimboPackets;
import top.yzljc.limbo.protocol.LimboMessages;
import top.yzljc.limbo.util.MinecraftTypes;
import top.yzljc.limbo.via.ViaSupport;
import top.yzljc.limbo.world.FakeWorld;
import top.yzljc.limbo.world.PlayerWorld;
import top.yzljc.limbo.proxy.*;
import com.viaversion.viaversion.api.type.Types;
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion;
import java.net.InetSocketAddress;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {
    private enum State { HANDSHAKE, STATUS, LOGIN, PLAY, CLOSED }
    private static final Logger LOGGER = Logger.getLogger("Limbo");
    private final LimboServer server;
    private final UserConnection connection;
    private State state = State.HANDSHAKE;
    private UUID uuid;
    private String handshakeHost;
    private PlayerProfile profile;
    private int skinParts = 0x7f;
    private boolean tabHatDirty = true;
    private ScheduledFuture<?> heartbeat;
    private ScheduledFuture<?> loginDeadline;
    private boolean awaitingKeepAlive;
    private boolean sentStatus;
    private int keepAliveId;
    private long keepAliveSent;
    private long lastKeepAlive;
    private final PlayerWorld world;
    private ChannelHandlerContext context;

    public ClientHandler(LimboServer server, UserConnection connection) {
        this.server = server;
        this.connection = connection;
        this.world = new PlayerWorld(server.world(), server.config().map(), server.config().viewDistance());
    }
    @Override public void channelActive(ChannelHandlerContext ctx) {
        context = ctx;
        loginDeadline = ctx.executor().schedule(() -> {
            if (state != State.PLAY) ctx.close();
        }, server.config().timeoutSeconds(), TimeUnit.SECONDS);
    }
    @Override protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        int id = VarInts.read(buf);
        switch (state) {
            case HANDSHAKE -> handshake(ctx, id, buf);
            case STATUS -> status(ctx, id, buf);
            case LOGIN -> login(ctx, id, buf);
            case PLAY -> play(ctx, id, buf);
            case CLOSED -> { }
        }
    }
    private void handshake(ChannelHandlerContext ctx, int id, ByteBuf b) {
        require(id == 0, "Expected handshake");
        int protocol = VarInts.read(b);
        handshakeHost = MinecraftTypes.readString(b, 32767); // Bungee appends profile properties to the host.
        b.readUnsignedShort();
        int next = VarInts.read(b);
        require(!b.isReadable(), "Trailing handshake data");
        require(next == 1 || next == 2, "Invalid next state");
        state = next == 1 ? State.STATUS : State.LOGIN;
        if (state == State.LOGIN && (protocol != 47 || !ViaSupport.supported(connection.getProtocolInfo().protocolVersion()))) {
            disconnect(ctx, "Supported clients: Minecraft Java 1.8 - 26.3 (release versions)");
            return;
        }
        if (state == State.LOGIN && server.config().proxy().mode() != ProxySettings.Mode.NONE
                && !server.config().proxy().trusted(((InetSocketAddress) ctx.channel().remoteAddress()).getAddress())) {
            LOGGER.warning("Rejected proxy connection from " + ctx.channel().remoteAddress()
                    + ": source address is not in proxy-trusted-addresses");
            disconnect(ctx, "Connect through a trusted proxy.");
        }
    }
    private void status(ChannelHandlerContext ctx, int id, ByteBuf b) {
        if (id == 0 && !sentStatus) {
            require(!b.isReadable(), "Invalid status request");
            sentStatus = true;
            JsonObject root = new JsonObject();
            JsonObject version = new JsonObject();
            version.addProperty("name", "Limbo 1.8 - 26.3");
            version.addProperty("protocol", 47); // ViaVersion rewrites to the client's supported protocol.
            root.add("version", version);
            JsonObject players = new JsonObject();
            players.addProperty("max", server.config().maxPlayers());
            players.addProperty("online", server.onlinePlayers());
            root.add("players", players);
            JsonObject description = new JsonObject();
            description.addProperty("text", server.config().motd());
            root.add("description", description);
            ctx.writeAndFlush(LimboPackets.packet(0, out -> MinecraftTypes.writeString(out, root.toString())));
        } else if (id == 1 && sentStatus) {
            require(b.readableBytes() == 8, "Invalid ping");
            long value = b.readLong();
            ctx.writeAndFlush(LimboPackets.packet(1, out -> out.writeLong(value))).addListener(ChannelFutureListener.CLOSE);
            state = State.CLOSED;
        } else {
            throw new CorruptedFrameException("Unexpected status packet");
        }
    }
    private void login(ChannelHandlerContext ctx, int id, ByteBuf b) {
        require(id == 0, "Expected login start");
        String username = MinecraftTypes.readString(b, 16);
        require(username.matches("[a-zA-Z0-9_]{1,16}") && !b.isReadable(), "Invalid username");
        var proxy = server.config().proxy();
        if (proxy.mode() == ProxySettings.Mode.VELOCITY) {
            if (connection.getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_13)) {
                disconnect(ctx, "Velocity modern forwarding requires 1.13+. Use legacy forwarding for older clients.");
                return;
            }
            ctx.pipeline().get(VelocityLoginHandler.class).request(username,
                    profile -> finishLogin(ctx, profile), reason -> disconnect(ctx, reason));
            return;
        }
        if (proxy.mode() == ProxySettings.Mode.BUNGEE) {
            PlayerProfile profile;
            try { profile = Forwarding.bungee(handshakeHost, username); }
            catch (RuntimeException e) {
                boolean missing = handshakeHost.indexOf('\0') < 0;
                // Do not log the host/payload or parser messages that may contain profile data.
                String reason = missing ? "No forwarding fields received; enable ip_forward: true in the proxy config.yml and restart the proxy"
                        : e instanceof CorruptedFrameException ? e.getMessage() : "Malformed forwarded profile data";
                LOGGER.warning("Rejected BungeeCord login from " + ctx.channel().remoteAddress()
                        + " (client " + connection.getProtocolInfo().protocolVersion().getName() + "): " + reason);
                disconnect(ctx, missing
                        ? "BungeeCord IP forwarding is missing. Set ip_forward: true in the proxy config.yml, then restart the proxy."
                        : "Invalid BungeeCord forwarding data. Check the Limbo server log.");
                return;
            }
            finishLogin(ctx, profile);
        } else {
            if (handshakeHost.indexOf('\0') >= 0) { disconnect(ctx, "Proxy forwarding is not enabled."); return; }
            finishLogin(ctx, PlayerProfile.offline(username, ((InetSocketAddress) ctx.channel().remoteAddress()).getAddress()));
        }
    }
    private void finishLogin(ChannelHandlerContext ctx, PlayerProfile profile) {
        if (state != State.LOGIN) return;
        String username = profile.username();
        uuid = profile.uuid();
        if (!server.admit(uuid, ctx.channel())) {
            disconnect(ctx, "Server full or player already connected");
            return;
        }
        this.profile = profile;
        connection.getProtocolInfo().setUuid(uuid);
        connection.getProtocolInfo().setUsername(username);
        ctx.writeAndFlush(LimboPackets.login(uuid, username));
        state = State.PLAY;
        loginDeadline.cancel(false);
        ctx.write(LimboPackets.join());
        ctx.write(LimboPackets.brand(server.config().serverBrand()));
        ctx.write(LimboPackets.playerInfo(profile));
        ctx.write(LimboPackets.skinParts(skinParts));
        ctx.write(LimboPackets.spawn(server.config().map()));
        ctx.write(LimboPackets.abilities(connection.getProtocolInfo().protocolVersion()
                .olderThan(ProtocolVersion.v1_21_4)));
        ctx.write(LimboPackets.time());
        ctx.write(LimboPackets.inventory(world.inventory()));
        for (var chunk : world.chunks()) ctx.write(LimboPackets.chunk(server.world(), chunk.x(), chunk.z()));
        // A sign's block entity must exist before its text update reaches the client.
        for (var sign : server.world().signs()) ctx.write(LimboPackets.sign(sign));
        ctx.write(LimboPackets.position(server.config().map()));
        for (String line : LimboMessages.WELCOME) ctx.write(LimboPackets.chat(line));
        ctx.flush();
        lastKeepAlive = System.nanoTime();
        heartbeat = ctx.executor().scheduleAtFixedRate(() -> heartbeat(ctx), 1, 1, TimeUnit.SECONDS);
        LOGGER.info(username + " joined with " + connection.getProtocolInfo().protocolVersion().getName());
    }
    private void heartbeat(ChannelHandlerContext ctx) {
        if (state != State.PLAY) return;
        long now = System.nanoTime();
        if (awaitingKeepAlive) {
            if (now - keepAliveSent >= TimeUnit.SECONDS.toNanos(server.config().timeoutSeconds())) {
                disconnect(ctx, "KeepAlive timed out");
            }
        } else if (now - lastKeepAlive >= TimeUnit.SECONDS.toNanos(server.config().keepAliveSeconds())) {
            keepAliveId = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
            awaitingKeepAlive = true;
            keepAliveSent = now;
            lastKeepAlive = now;
            ctx.writeAndFlush(LimboPackets.keepAlive(keepAliveId));
        }
    }
    private void play(ChannelHandlerContext ctx, int id, ByteBuf b) {
        syncTabHat();
        if (id == 0) {
            int reply = VarInts.read(b);
            require(!b.isReadable() && awaitingKeepAlive && reply == keepAliveId, "Invalid KeepAlive response");
            awaitingKeepAlive = false;
        } else if (id == 0x01) {
            String message = MinecraftTypes.readString(b, 100);
            if (message.trim().equalsIgnoreCase("/limbo")) {
                for (String line : LimboMessages.HELP) ctx.write(LimboPackets.chat(line));
                ctx.flush();
            }
        } else if (id == 0x15) {
            MinecraftTypes.readString(b, 16); // locale
            b.readByte(); b.readByte(); b.readBoolean(); // view distance, chat mode, chat colors
            skinParts = b.readUnsignedByte() & 0x7f;
            require(!b.isReadable(), "Invalid client settings");
            ctx.writeAndFlush(LimboPackets.skinParts(skinParts));
            tabHatDirty = true;
            syncTabHat();
        } else if (id == 0x04 || id == 0x06) {
            double x = b.readDouble(), y = b.readDouble(), z = b.readDouble();
            require(Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z), "Non-finite position");
            world.move(x, y, z);
            if (id == 0x06) updateLook(b);
            b.readBoolean();
            // No world boundary, void damage or return teleport in this fake world.
        } else if (id == 0x05) {
            updateLook(b);
            b.readBoolean();
        } else if (id == 0x0b) {
            VarInts.read(b); // entity ID
            int action = VarInts.read(b);
            if (action == 0 || action == 1) world.sneak(action == 0,
                    connection.getProtocolInfo().protocolVersion().newerThanOrEqualTo(ProtocolVersion.v1_14));
        } else if (id == 0x09) {
            world.select(b.readShort());
        } else if (id == 0x0e) {
            int window = b.readUnsignedByte(), slot = b.readShort(), button = b.readUnsignedByte();
            short action = b.readShort();
            int mode = b.readUnsignedByte();
            var reported = Types.ITEM1_8.read(b);
            if (window == 0) {
                // In 1.8 a locally picked stack first reaches us in the click/placement packet.
                if (connection.getProtocolInfo().protocolVersion().equals(ProtocolVersion.v1_8)
                        && slot >= 36 && slot <= 44 && world.inventory().get(slot) == null && reported != null)
                    world.inventory().set(slot, reported);
                world.inventory().click(slot, button, mode);
                ctx.write(LimboPackets.transaction(window, action));
                syncInventory(ctx);
            }
        } else if (id == 0x0d) {
            if (b.readUnsignedByte() == 0) {
                world.inventory().close();
                syncInventory(ctx);
            }
        } else if (id == 0x10) {
            int slot = b.readShort();
            var item = Types.ITEM1_8.read(b);
            if (slot >= 36 && slot < 45 && connection.getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_21_4)) {
                world.setSlot(slot - 36, item);
            }
        } else if (id == 0x07) {
            int action = b.readUnsignedByte();
            long position = b.readLong();
            b.readByte();
            if (action >= 0 && action <= 2) {
                if (!world.contains(position)) return;
                boolean broken = world.dig(action, position);
                if (broken || action == 2) { writeBlock(ctx, position); ctx.flush(); }
            } else if (action == 3 || action == 4) {
                // 1.8 only sends the drop request; it does not remove the item locally.
                // Its middle-click item can still be client-only: sending our stale slot
                // would erase/replace that item. Reject silently on native 1.8.
                // Modern clients predict drops, so restore their authoritative held stack.
                if (!connection.getProtocolInfo().protocolVersion().equals(ProtocolVersion.v1_8))
                    ctx.writeAndFlush(LimboPackets.slot(36 + world.selected(), world.held()));
            }
        } else if (id == 0x08) {
            long clicked = b.readLong();
            int face = b.readUnsignedByte();
            var carried = Types.ITEM1_8.read(b);
            float hitX = b.readUnsignedByte() / 16f, hitY = b.readUnsignedByte() / 16f, hitZ = b.readUnsignedByte() / 16f;
            if (face > 5) return;
            // 1.8 survival pick-block is client-local; its placement packet supplies the picked item.
            // Newer Via-translated carried items may be stale; our inventory owns the count.
            if (connection.getProtocolInfo().protocolVersion().equals(ProtocolVersion.v1_8)) world.setSlot(world.selected(), carried);
            var placement = world.place(clicked, face, hitX, hitY, hitZ);
            if (world.contains(clicked)) writeBlock(ctx, clicked);
            if (placement.target() != clicked && world.contains(placement.target()))
                writeBlock(ctx, placement.target());
            ctx.writeAndFlush(LimboPackets.slot(36 + world.selected(), world.held()));
        }
        // Teleport acknowledgements and other gameplay packets
        // are handled by ViaVersion or intentionally have no effect in Limbo.
    }
    private void writeBlock(ChannelHandlerContext ctx, long position) {
        int state = world.block(position);
        ctx.write(LimboPackets.block(position, state));
        var sign = server.world().sign(position);
        if (sign != null && (state >> 4 == 63 || state >> 4 == 68)) ctx.write(LimboPackets.sign(sign));
    }
    private void syncTabHat() {
        if (!tabHatDirty || connection.getProtocolInfo().protocolVersion().olderThan(ProtocolVersion.v1_21_4)
                || connection.getProtocolInfo().getClientState() != com.viaversion.viaversion.api.protocol.packet.State.PLAY) return;
        // Wait for PLAY: sending modern packets during configuration bypasses Via's legacy packet queue.
        ViaSupport.sendTabHat(connection, uuid, (skinParts & 0x40) != 0);
        tabHatDirty = false;
    }
    private void syncInventory(ChannelHandlerContext ctx) {
        ctx.write(LimboPackets.inventory(world.inventory()));
        ctx.writeAndFlush(LimboPackets.cursor(world.inventory().cursor()));
    }
    private void updateLook(ByteBuf b) {
        float yaw = b.readFloat(), pitch = b.readFloat();
        require(Float.isFinite(yaw) && Float.isFinite(pitch), "Non-finite rotation");
        world.look(yaw);
    }
    /** Verified proxy identity, or offline direct identity; available after successful admission. */
    public PlayerProfile profile() { return profile; }

    /** Called by ViaVersion for 1.21.4+ pick-block requests. */
    public void pickBlock(int x, int y, int z) {
        if (state != State.PLAY || context == null || y < 0 || y > 255) return;
        var item = world.pick(FakeWorld.position(x, y, z));
        if (item != null) context.writeAndFlush(LimboPackets.slot(36 + world.selected(), item));
    }
    private void disconnect(ChannelHandlerContext ctx, String reason) {
        if (state == State.CLOSED) return;
        boolean play = state == State.PLAY;
        state = State.CLOSED;
        ctx.writeAndFlush(LimboPackets.disconnect(play, reason)).addListener(ChannelFutureListener.CLOSE);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new CorruptedFrameException(message);
    }
    @Override public void channelInactive(ChannelHandlerContext ctx) {
        state = State.CLOSED;
        if (heartbeat != null) heartbeat.cancel(false);
        if (loginDeadline != null) loginDeadline.cancel(false);
        server.remove(uuid, ctx.channel());
    }
    @Override public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        LOGGER.log(Level.FINE, "Closing connection " + ctx.channel().remoteAddress(), cause);
        ctx.close();
    }
}
