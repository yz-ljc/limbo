package top.yzljc.limbo.protocol.packet.clientbound;

import io.netty.buffer.ByteBuf;

public interface ClientboundPacket {

    int packetId();

    void write(ByteBuf buf);

}