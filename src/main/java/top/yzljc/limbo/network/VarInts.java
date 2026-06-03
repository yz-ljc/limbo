package top.yzljc.limbo.network;

import io.netty.buffer.ByteBuf;

public final class VarInts {

    private VarInts() {
    }

    public static int read(ByteBuf buf) {

        int value = 0;
        int position = 0;

        byte current;

        do {

            current = buf.readByte();

            value |=
                    (current & 0x7F)
                            << position;

            position += 7;

            if (position >= 32) {
                throw new RuntimeException(
                        "VarInt too big"
                );
            }

        } while ((current & 0x80) != 0);

        return value;
    }

    public static void write(
            ByteBuf buf,
            int value
    ) {

        do {

            byte temp =
                    (byte) (value & 0x7F);

            value >>>= 7;

            if (value != 0) {
                temp |= 0x80;
            }

            buf.writeByte(temp);

        } while (value != 0);

    }
}