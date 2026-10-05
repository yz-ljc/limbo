package top.yzljc.limbo.proxy;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import top.yzljc.limbo.network.VarInts;
import top.yzljc.limbo.protocol.LimboPackets;
import top.yzljc.limbo.util.MinecraftTypes;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/** Login plugin packets must be intercepted before Via's translation to protocol 47. */
public final class VelocityLoginHandler extends ChannelInboundHandlerAdapter {
    private final ProxySettings settings;
    private ChannelHandlerContext context;
    private int query;
    private String username;
    private Consumer<PlayerProfile> accepted;
    private Consumer<String> rejected;
    private boolean pending;
    public VelocityLoginHandler(ProxySettings settings) { this.settings = settings; }
    @Override public void handlerAdded(ChannelHandlerContext ctx) { context = ctx; }
    public void request(String username, Consumer<PlayerProfile> accepted, Consumer<String> rejected) {
        if (pending) throw new IllegalStateException("Forwarding already requested");
        this.username = username; this.accepted = accepted; this.rejected = rejected;
        query = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        pending = true;
        // Bypass Via: Login Plugin Request is absent in the internal 1.8 protocol.
        context.writeAndFlush(LimboPackets.packet(4, b -> {
            VarInts.write(b, query); MinecraftTypes.writeString(b, "velocity:player_info");
            b.writeByte(1); // Negotiate basic authenticated profile forwarding; no signed chat key needed.
        }));
    }
    @Override public void channelRead(ChannelHandlerContext ctx, Object message) {
        if (!pending || !(message instanceof ByteBuf b)) { ctx.fireChannelRead(message); return; }
        pending = false;
        PlayerProfile profile;
        try {
            if (VarInts.read(b) != 2 || VarInts.read(b) != query || !b.readBoolean())
                throw new IllegalArgumentException("Missing Velocity forwarding response");
            profile = Forwarding.velocity(b, settings, username);
        } catch (RuntimeException e) {
            rejected.accept("Unable to verify Velocity forwarding. Connect through the configured proxy.");
            return;
        } finally { b.release(); }
        accepted.accept(profile);
    }
}
