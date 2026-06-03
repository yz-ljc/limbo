package top.yzljc.limbo.protocol.packet.clientbound.play;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

/**
 * Teleports the player to a position.  For a limbo server we place the
 * player at (0, 64, 0) with no movement.
 */
public class PlayerPositionAndLookPacket implements ClientboundPacket {

    private final int protocolVersion;

    public PlayerPositionAndLookPacket(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public int packetId() {
        if (protocolVersion == 47) return 0x08; // 1.8
        if (protocolVersion >= 766) return 0x3E; // 1.20.5+
        if (protocolVersion >= 764) return 0x3A; // 1.20.2–1.20.4
        if (protocolVersion >= 761) return 0x39; // 1.19.3–1.20.1
        if (protocolVersion >= 759) return 0x36; // 1.19–1.19.2
        if (protocolVersion >= 755) return 0x38; // 1.17–1.18.2
        if (protocolVersion >= 751) return 0x36; // 1.16.2
        if (protocolVersion >= 735) return 0x36; // 1.16
        if (protocolVersion >= 573) return 0x36; // 1.15
        if (protocolVersion >= 477) return 0x35; // 1.14
        if (protocolVersion >= 393) return 0x32; // 1.13
        return 0x2F; // 1.9–1.12
    }

    @Override
    public void write(ByteBuf buf) {
        // Coordinates
        buf.writeDouble(0.0);  // X
        buf.writeDouble(64.0); // Y  (safe above void)
        buf.writeDouble(0.0);  // Z
        buf.writeFloat(0.0f);  // Yaw
        buf.writeFloat(0.0f);  // Pitch

        if (protocolVersion <= 47) {
            // 1.8: flags + teleport-id
            buf.writeByte(0);   // Flags
        } else {
            // 1.9+: flags (byte)
            buf.writeByte(0);   // Flags (0 = absolute position & rotation)

            if (protocolVersion >= 766) {
                // 1.20.5+: teleport ID as VarInt
                MinecraftTypes.writeVarInt(buf, 0);
            } else if (protocolVersion >= 107) {
                // 1.9+: teleport ID as VarInt
                MinecraftTypes.writeVarInt(buf, 0);
            }
        }

        if (protocolVersion >= 755) {
            // Dismount vehicle flag (1.17+)
            buf.writeBoolean(false);
        }
    }
}
