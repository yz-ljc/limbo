package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class PacketFrameDecoder
        extends ByteToMessageDecoder {

    @Override
    protected void decode(
            ChannelHandlerContext ctx,
            ByteBuf in,
            List<Object> out
    ) {

        in.markReaderIndex();

        if (!in.isReadable()) {
            return;
        }

        int length;

        try {
            length = VarInts.read(in);
        } catch (Exception ex) {
            in.resetReaderIndex();
            return;
        }

        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }

        out.add(
                in.readRetainedSlice(length)
        );
    }
}