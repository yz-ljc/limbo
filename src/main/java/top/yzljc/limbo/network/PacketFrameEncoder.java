package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;

public final class PacketFrameEncoder {

    private PacketFrameEncoder() {
    }

    public static ByteBuf encode(
            ClientboundPacket packet
    ) {

        ByteBuf data =
                Unpooled.buffer();

        VarInts.write(
                data,
                packet.packetId()
        );

        packet.write(data);

        ByteBuf frame =
                Unpooled.buffer();

        VarInts.write(
                frame,
                data.readableBytes()
        );

        frame.writeBytes(data);

        return frame;

    }

}