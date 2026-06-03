package top.yzljc.limbo.protocol.packet.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;

public class ConfigurationFinishPacket implements ClientboundPacket {

    private final int protocolVersion;

    public ConfigurationFinishPacket(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public int packetId() {
        if (protocolVersion >= 766) return 0x03; // 1.20.5+
        return 0x02; // 1.20.2-1.20.4
    }

    @Override
    public void write(ByteBuf buf) {
        // Empty payload
    }
}

