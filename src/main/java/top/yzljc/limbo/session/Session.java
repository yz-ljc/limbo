package top.yzljc.limbo.session;

import io.netty.channel.Channel;
import top.yzljc.limbo.network.PacketFrameEncoder;
import top.yzljc.limbo.protocol.ConnectionState;
import top.yzljc.limbo.protocol.ProtocolAdapter;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;

public class Session {

    private final Channel channel;

    private ConnectionState state =
            ConnectionState.HANDSHAKE;

    private int protocolVersion;

    private ProtocolAdapter protocol;

    public Session(Channel channel) {
        this.channel = channel;
    }

    public Channel getChannel() {
        return channel;
    }

    public ConnectionState getState() {
        return state;
    }

    public void setState(ConnectionState state) {
        this.state = state;
    }

    public int getProtocolVersion() {
        return protocolVersion;
    }

    public void setProtocolVersion(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public ProtocolAdapter getProtocol() {
        return protocol;
    }

    public void setProtocol(ProtocolAdapter protocol) {
        this.protocol = protocol;
    }

    public void send(
            ClientboundPacket packet
    ) {

        channel.writeAndFlush(
                PacketFrameEncoder.encode(
                        packet
                )
        );

    }

}