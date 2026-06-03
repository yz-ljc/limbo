package top.yzljc.limbo.protocol.packet.clientbound.play;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

public class GenericKeepAlivePacket implements ClientboundPacket {

    private final int protocolVersion;
    private final long time;
    private final boolean isConfiguration;

    public GenericKeepAlivePacket(int protocolVersion, long time, boolean isConfiguration) {
        this.protocolVersion = protocolVersion;
        this.time = time;
        this.isConfiguration = isConfiguration;
    }

    public GenericKeepAlivePacket(int protocolVersion, long time) {
        this(protocolVersion, time, false);
    }

    @Override
    public int packetId() {
        if (isConfiguration) {
            // Configuration keep alive:
            // 1.20.2 (764): 0x03
            // 1.20.5 (766) - 1.21.x: 0x04
            if (protocolVersion >= 766) return 0x04;
            return 0x03;
        }

        if (protocolVersion == 47) return 0x00; // 1.8
        if (protocolVersion >= 764) return 0x24; // 1.20.2+ PLAY
        if (protocolVersion >= 761) return 0x23; // 1.19.3 - 1.20.1 PLAY
        if (protocolVersion >= 759) return 0x20; // 1.19 - 1.19.2 PLAY
        if (protocolVersion >= 755) return 0x21; // 1.17 - 1.18.2 PLAY
        if (protocolVersion >= 751) return 0x1F; // 1.16.2 - 1.16.5 PLAY
        if (protocolVersion >= 735) return 0x20; // 1.16 - 1.16.1 PLAY
        if (protocolVersion >= 573) return 0x21; // 1.15.x PLAY
        if (protocolVersion >= 477) return 0x20; // 1.14.x PLAY
        if (protocolVersion >= 393) return 0x21; // 1.13.x PLAY
        return 0x1F; // default for 1.9 - 1.12.2 PLAY
    }

    @Override
    public void write(ByteBuf buf) {
        if (protocolVersion <= 338) { // 1.12.2 and below
            MinecraftTypes.writeVarInt(buf, (int) time);
        } else {
            buf.writeLong(time);
        }
    }
}
