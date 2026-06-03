package top.yzljc.limbo.protocol.packet.clientbound.status;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;

public class PingResponsePacket implements ClientboundPacket {

    private final long payload;

    public PingResponsePacket(long payload) {
        this.payload = payload;
    }

    @Override
    public int packetId() {
        return 0x01;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeLong(payload);
    }
}

