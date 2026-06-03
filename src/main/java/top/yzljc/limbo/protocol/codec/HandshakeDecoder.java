package top.yzljc.limbo.protocol.codec;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.network.VarInts;
import top.yzljc.limbo.protocol.packet.handshake.HandshakePacket;
import top.yzljc.limbo.util.MinecraftTypes;

public final class HandshakeDecoder {

    private HandshakeDecoder() {
    }

    public static HandshakePacket decode(
            ByteBuf buf
    ) {

        int protocolVersion =
                VarInts.read(buf);

        String host =
                MinecraftTypes.readString(
                        buf
                );

        int port =
                buf.readUnsignedShort();

        int nextState =
                VarInts.read(buf);

        return new HandshakePacket(
                protocolVersion,
                host,
                port,
                nextState
        );
    }
}