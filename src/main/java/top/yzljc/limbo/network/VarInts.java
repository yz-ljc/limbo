package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;

public final class VarInts {
    private VarInts() {}
    public static int read(ByteBuf buf) {
        int value = 0;
        for (int i = 0; i < 5; i++) {
            int b = buf.readUnsignedByte();
            if (i == 4 && (b & 0xf0) != 0) throw new CorruptedFrameException("VarInt exceeds 32 bits");
            value |= (b & 0x7f) << (7 * i);
            if ((b & 0x80) == 0) return value;
        }
        throw new CorruptedFrameException("VarInt exceeds five bytes");
    }
    public static void write(ByteBuf buf, int value) {
        do {
            int b = value & 0x7f;
            value >>>= 7;
            buf.writeByte(value == 0 ? b : b | 0x80);
        } while (value != 0);
    }
}
