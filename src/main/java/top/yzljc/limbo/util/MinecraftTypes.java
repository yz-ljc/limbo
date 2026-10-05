package top.yzljc.limbo.util;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.CorruptedFrameException;
import top.yzljc.limbo.network.VarInts;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

public final class MinecraftTypes {
    private MinecraftTypes() {}
    public static String readString(ByteBuf buf, int maxCharacters) {
        int length = VarInts.read(buf);
        if (length < 0 || length > maxCharacters * 3 || length > buf.readableBytes())
            throw new CorruptedFrameException("Invalid string length");
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        try {
            String value = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString();
            if (value.length() > maxCharacters) throw new CorruptedFrameException("String too long");
            return value;
        } catch (CharacterCodingException e) {
            throw new CorruptedFrameException("Invalid UTF-8", e);
        }
    }
    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        VarInts.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }
}
