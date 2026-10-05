package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;
import io.netty.handler.codec.CorruptedFrameException;
import java.util.List;

/** Minecraft uses a three-byte VarInt length, not an unrestricted VarInt. */
public final class PacketFrameDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.markReaderIndex();
        int length = 0;
        for (int i = 0; i < 3; i++) {
            if (!in.isReadable()) {
                in.resetReaderIndex();
                return;
            }
            int b = in.readUnsignedByte();
            length |= (b & 0x7f) << (7 * i);
            if ((b & 0x80) != 0) continue;
            if (length == 0) throw new CorruptedFrameException("Empty packet");
            if (in.readableBytes() < length) {
                in.resetReaderIndex();
                return;
            }
            out.add(in.readRetainedSlice(length));
            return;
        }
        throw new CorruptedFrameException("Packet length exceeds 21 bits");
    }
}
