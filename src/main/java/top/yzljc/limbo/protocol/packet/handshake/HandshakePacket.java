package top.yzljc.limbo.protocol.packet.handshake;

import top.yzljc.limbo.protocol.Packet;

public class HandshakePacket
        implements Packet {

    private final int protocolVersion;
    private final String host;
    private final int port;
    private final int nextState;

    public HandshakePacket(
            int protocolVersion,
            String host,
            int port,
            int nextState
    ) {
        this.protocolVersion = protocolVersion;
        this.host = host;
        this.port = port;
        this.nextState = nextState;
    }

    public int protocolVersion() {
        return protocolVersion;
    }

    public String host() {
        return host;
    }

    public int port() {
        return port;
    }

    public int nextState() {
        return nextState;
    }
}