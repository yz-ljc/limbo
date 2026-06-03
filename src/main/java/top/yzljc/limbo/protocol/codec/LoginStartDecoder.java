package top.yzljc.limbo.protocol.codec;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.login.LoginStartPacket;
import top.yzljc.limbo.util.MinecraftTypes;

public final class LoginStartDecoder {

    private LoginStartDecoder() {
    }

    public static LoginStartPacket decode(
            ByteBuf buf
    ) {

        String username =
                MinecraftTypes.readString(
                        buf
                );

        return new LoginStartPacket(
                username
        );
    }
}