package top.yzljc.limbo.protocol.packet.clientbound.login;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

import java.util.UUID;

public class LoginSuccessPacket
        implements ClientboundPacket {

    private final int protocolVersion;
    private final UUID uuid;
    private final String username;

    public LoginSuccessPacket(
            int protocolVersion,
            UUID uuid,
            String username
    ) {
        this.protocolVersion = protocolVersion;
        this.uuid = uuid;
        this.username = username;
    }

    @Override
    public int packetId() {
        return 0x02;
    }

    @Override
    public void write(
            ByteBuf buf
    ) {

        if (protocolVersion >= 707) {
            MinecraftTypes.writeUuid(buf, uuid);
        } else {
            MinecraftTypes.writeString(buf, uuid.toString());
        }

        MinecraftTypes.writeString(
                buf,
                username
        );

        if (protocolVersion >= 759) {
            MinecraftTypes.writeVarInt(
                    buf,
                    0
            );
        }

        // strict_error_handling boolean was added in 1.20.2 (764) but removed in 1.20.5 (766)
        if (protocolVersion >= 764 && protocolVersion <= 765) {
            buf.writeBoolean(false);
        }
    }
}