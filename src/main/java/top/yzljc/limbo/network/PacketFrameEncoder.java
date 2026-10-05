package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.TooLongFrameException;

/** Runs AFTER ViaVersion on outgoing packet payloads. Netty releases input buffers. */
public final class PacketFrameEncoder extends MessageToByteEncoder<ByteBuf> {
    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf message, ByteBuf out) {
        int length = message.readableBytes();
        if (length == 0 || length > 0x1fffff) throw new TooLongFrameException("Invalid packet size: " + length);
        VarInts.write(out, length);
        out.writeBytes(message);
    }
}
