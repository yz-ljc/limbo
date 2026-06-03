package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import top.yzljc.limbo.protocol.ConnectionState;
import top.yzljc.limbo.protocol.RegistryData;
import top.yzljc.limbo.protocol.codec.HandshakeDecoder;
import top.yzljc.limbo.protocol.codec.LoginStartDecoder;
import top.yzljc.limbo.protocol.packet.clientbound.configuration.ClientboundUpdateTagsPacket;
import top.yzljc.limbo.protocol.packet.clientbound.configuration.ConfigurationFinishPacket;
import top.yzljc.limbo.protocol.packet.clientbound.configuration.ConfigurationRegistryDataPacket;
import top.yzljc.limbo.protocol.packet.clientbound.login.LoginSuccessPacket;
import top.yzljc.limbo.protocol.packet.clientbound.play.GenericKeepAlivePacket;
import top.yzljc.limbo.protocol.packet.clientbound.play.JoinGamePacket;
import top.yzljc.limbo.protocol.packet.clientbound.play.PlayerPositionAndLookPacket;
import top.yzljc.limbo.protocol.packet.clientbound.status.PingResponsePacket;
import top.yzljc.limbo.protocol.packet.clientbound.status.StatusResponsePacket;
import top.yzljc.limbo.protocol.packet.handshake.HandshakePacket;
import top.yzljc.limbo.protocol.packet.login.LoginStartPacket;
import top.yzljc.limbo.session.Session;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ClientHandler extends SimpleChannelInboundHandler<ByteBuf> {

    private Session session;
    private ChannelHandlerContext ctx;
    private ScheduledFuture<?> keepAliveTask;

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.ctx = ctx;
        this.session = new Session(ctx.channel());

        keepAliveTask = ctx.executor().scheduleAtFixedRate(
                () -> {
                    if (session.getState() == ConnectionState.PLAY
                            || session.getState() == ConnectionState.CONFIGURATION) {
                        boolean isConfig = session.getState() == ConnectionState.CONFIGURATION;
                        session.send(new GenericKeepAlivePacket(
                                session.getProtocolVersion(),
                                System.currentTimeMillis(),
                                isConfig
                        ));
                    }
                },
                10, 10, TimeUnit.SECONDS
        );

        System.out.println("Client connected");
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, ByteBuf buf) {
        int packetId = VarInts.read(buf);
        switch (session.getState()) {
            case HANDSHAKE -> handleHandshake(buf, packetId);
            case STATUS -> handleStatus(buf, packetId);
            case LOGIN -> handleLogin(buf, packetId);
            case CONFIGURATION -> handleConfiguration(buf, packetId);
            case PLAY -> handlePlay(buf, packetId);
        }
    }

    private void handleLogin(ByteBuf buf, int packetId) {
        if (packetId == 0x00) {
            LoginStartPacket packet = LoginStartDecoder.decode(buf);
            System.out.println("Login: " + packet.username());

            session.send(new LoginSuccessPacket(
                    session.getProtocolVersion(),
                    UUID.randomUUID(),
                    packet.username()
            ));

            if (session.getProtocolVersion() >= 764) {
                // 1.20.2+ waits for Login Acknowledged, then transitions to CONFIGURATION
                return;
            }

            // Pre-1.20.2: go directly to PLAY
            session.setState(ConnectionState.PLAY);
            session.send(new JoinGamePacket(session.getProtocolVersion()));
            session.send(new PlayerPositionAndLookPacket(session.getProtocolVersion()));

        } else if (packetId == 0x03 && session.getProtocolVersion() >= 764) {
            // Login Acknowledged (1.20.2+)
            System.out.println("Login acknowledged, entering configuration state");
            session.setState(ConnectionState.CONFIGURATION);

            // Send registry data + tags
            sendAllRegistryData();

            // Tell the client configuration is done
            session.send(new ConfigurationFinishPacket(session.getProtocolVersion()));
            System.out.println("Sent registry data + tags + configuration finish");
        }
    }

    /**
     * Send all registry data (elements + tags).
     */
    private void sendAllRegistryData() {
        int pv = session.getProtocolVersion();
        Collection<RegistryData.RegistryDef> registries = RegistryData.getRegistries();

        // Phase 1: registry elements
        int count = 0;
        for (RegistryData.RegistryDef registry : registries) {
            session.send(new ConfigurationRegistryDataPacket(pv, registry));
            count++;
        }
        System.out.println("Sent " + count + " registry data packets");

        // Phase 2: tags
        session.send(new ClientboundUpdateTagsPacket(pv, registries));
        System.out.println("Sent update tags packet");
    }

    private void handleHandshake(ByteBuf buf, int packetId) {
        if (packetId != 0x00) return;

        HandshakePacket packet = HandshakeDecoder.decode(buf);
        System.out.println("Protocol " + packet.protocolVersion());
        session.setProtocolVersion(packet.protocolVersion());

        if (packet.nextState() == 2) {
            session.setState(ConnectionState.LOGIN);
        } else if (packet.nextState() == 1) {
            session.setState(ConnectionState.STATUS);
        }
    }

    private void handleStatus(ByteBuf buf, int packetId) {
        if (packetId == 0x00) {
            String json = "{\"version\":{\"name\":\"Limbo Universal\",\"protocol\":" + session.getProtocolVersion() + "},\"players\":{\"max\":1,\"online\":0},\"description\":{\"text\":\"Limbo Server Universal\"}}";
            session.send(new StatusResponsePacket(json));
        } else if (packetId == 0x01) {
            long payload = buf.readLong();
            session.send(new PingResponsePacket(payload));
        }
    }

    private void handleConfiguration(ByteBuf buf, int packetId) {
        // Serverbound Finish Configuration / Acknowledge Finish Configuration
        if (packetId == 0x02 || (session.getProtocolVersion() >= 766 && packetId == 0x03)) {
            System.out.println("Configuration acknowledged, transitioning to PLAY state");
            session.setState(ConnectionState.PLAY);

            session.send(new JoinGamePacket(session.getProtocolVersion()));
            session.send(new PlayerPositionAndLookPacket(session.getProtocolVersion()));
            System.out.println("Sent JoinGame + PlayerPositionAndLook");
        }
    }

    private void handlePlay(ByteBuf buf, int packetId) {
        // Ignore all play packets — limbo mode
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        if (keepAliveTask != null) {
            keepAliveTask.cancel(false);
        }
        System.out.println("Client disconnected");
    }
}
