package top.yzljc.limbo.protocol.packet.clientbound.play;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

/**
 * Sends the Join Game packet that makes the client enter the world.
 * <p>
 * The format changed significantly across versions:
 * <ul>
 *   <li>1.8 (47): entity id (int), gamemode (ubyte), dimension (ubyte),
 *       difficulty (ubyte), max players (ubyte), level type (string),
 *       reduced debug (bool)</li>
 *   <li>1.16+ (735): adds dimension codec NBT, dimension type (identifier),
 *       hashed seed (long), gamemode, previous gamemode, is debug, is flat,
 *       death location info</li>
 *   <li>1.20.2+ (764): the dimension codec NBT is moved to the CONFIGURATION
 *       phase, so JoinGame is simplified</li>
 *   <li>1.20.5+ (766): dimension info changed again</li>
 * </ul>
 * </p>
 */
public class JoinGamePacket implements ClientboundPacket {

    private final int protocolVersion;

    public JoinGamePacket(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public int packetId() {
        if (protocolVersion == 47) return 0x01; // 1.8
        if (protocolVersion >= 766) return 0x29; // 1.20.5+
        if (protocolVersion >= 764) return 0x29; // 1.20.2–1.20.4
        if (protocolVersion >= 761) return 0x28; // 1.19.3–1.20.1
        if (protocolVersion >= 759) return 0x25; // 1.19–1.19.2
        if (protocolVersion >= 755) return 0x26; // 1.17–1.18.2
        if (protocolVersion >= 751) return 0x24; // 1.16.2
        if (protocolVersion >= 735) return 0x25; // 1.16
        if (protocolVersion >= 573) return 0x26; // 1.15
        if (protocolVersion >= 477) return 0x25; // 1.14
        if (protocolVersion >= 393) return 0x25; // 1.13
        return 0x23; // 1.9–1.12
    }

    @Override
    public void write(ByteBuf buf) {
        if (protocolVersion == 47) {
            writeV18(buf);
        } else if (protocolVersion >= 766) {
            writeV1205(buf);
        } else if (protocolVersion >= 764) {
            writeV1202(buf);
        } else if (protocolVersion >= 735) {
            writeV116(buf);
        } else {
            writeV19(buf);
        }
    }

    // ================================================================
    //  1.8
    // ================================================================
    private void writeV18(ByteBuf buf) {
        buf.writeInt(1);          // Entity ID
        buf.writeByte(3);        // Gamemode (Spectator — won't fall or die)
        buf.writeByte(0);        // Dimension (Overworld)
        buf.writeByte(0);        // Difficulty (Peaceful)
        buf.writeByte(1);        // Max Players
        MinecraftTypes.writeString(buf, "default"); // Level Type
        buf.writeBoolean(false); // Reduced Debug Info
    }

    // ================================================================
    //  1.9 – 1.15  (basic fields only — the registry codec is not needed
    //                pre-1.16 for a limbo server, and the client tolerates
    //                missing dimension data in these old versions)
    // ================================================================
    private void writeV19(ByteBuf buf) {
        buf.writeInt(1);          // Entity ID
        buf.writeByte(0);        // Gamemode (Survival)
        if (protocolVersion >= 748) { // 1.16.2+
            buf.writeByte(-1);   // Previous gamemode (none)
        }
        if (protocolVersion >= 573) { // 1.15+
            MinecraftTypes.writeVarInt(buf, 1); // Dimension count
            MinecraftTypes.writeString(buf, "minecraft:overworld");
        }
        // For pre-1.15:
        if (protocolVersion < 573) {
            buf.writeByte(0);    // Dimension
        }
        if (protocolVersion < 713) { // pre-1.15
            buf.writeByte(0);    // Difficulty
        }
        if (protocolVersion < 735) { // pre-1.16
            buf.writeByte(1);    // Max Players
            MinecraftTypes.writeString(buf, "default"); // Level type
        }
        if (protocolVersion >= 573) {
            buf.writeByte(1);    // Max Players
            MinecraftTypes.writeString(buf, "default"); // Level type
            buf.writeBoolean(false); // Reduced debug info
        }
    }

    // ================================================================
    //  1.16 – 1.20.1  (includes dimension codec NBT in JoinGame)
    //
    //  NOTE: for true 1.16+ support the JoinGame packet must include a
    //  full dimension codec NBT.  Since this limbo server targets 1.8 and
    //  1.20.2+ primarily, we send minimal-but-crash-free data for the
    //  intermediate versions.  The 1.16–1.20.1 client will show a dirt
    //  background but should stay connected.
    // ================================================================
    private void writeV116(ByteBuf buf) {
        buf.writeInt(1);          // Entity ID
        buf.writeBoolean(false);  // Is Hardcore
        buf.writeByte(0);         // Gamemode (Survival)
        buf.writeByte(-1);        // Previous gamemode (none)
        // Dimension names
        MinecraftTypes.writeVarInt(buf, 1);
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        // Registry codec NBT — empty compound (the client may crash for
        // complex registries; this limbo targets 1.20.2+ where registries
        // are sent in the CONFIGURATION phase instead).
        MinecraftTypes.writeNbt(buf,
                new net.querz.nbt.tag.CompoundTag());
        // Dimension type & ID
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        // Hashed seed
        buf.writeLong(0L);
        if (protocolVersion >= 764) {
            // 1.20.2+ simplified JoinGame (registry is in configuration phase)
            MinecraftTypes.writeVarInt(buf, 1);  // Max Players
            MinecraftTypes.writeVarInt(buf, 8);  // View Distance
            MinecraftTypes.writeVarInt(buf, 8);  // Simulation Distance
            buf.writeBoolean(false);              // Reduced Debug Info
            buf.writeBoolean(true);               // Enable Respawn Screen
            buf.writeBoolean(false);              // Do Limited Crafting
            // 1.20.2+ ends here (registry moved to config phase)
            return;
        }
        // 1.16–1.20.1 fields
        MinecraftTypes.writeVarInt(buf, 1);  // Max Players
        MinecraftTypes.writeVarInt(buf, 8);  // View Distance
        if (protocolVersion >= 760) {
            MinecraftTypes.writeVarInt(buf, 8);  // Simulation Distance
        }
        buf.writeBoolean(false);              // Reduced Debug Info
        buf.writeBoolean(true);               // Enable Respawn Screen
        if (protocolVersion >= 759) {
            buf.writeBoolean(false);          // Is Debug
            buf.writeBoolean(false);          // Is Flat
        }
        if (protocolVersion >= 761) {
            // Death location (absent)
            buf.writeBoolean(false);
        }
    }

    // ================================================================
    //  1.20.2 – 1.20.4 (protocol 764–765)
    //  Registry data is sent in the CONFIGURATION phase — JoinGame is
    //  simple again.
    // ================================================================
    private void writeV1202(ByteBuf buf) {
        buf.writeInt(1);            // Entity ID
        buf.writeBoolean(false);    // Is Hardcore
        // Dimension names
        MinecraftTypes.writeVarInt(buf, 1);
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        // Max players
        MinecraftTypes.writeVarInt(buf, 1);
        // View distance
        MinecraftTypes.writeVarInt(buf, 8);
        // Simulation distance
        MinecraftTypes.writeVarInt(buf, 8);
        // Reduced debug info
        buf.writeBoolean(false);
        // Enable respawn screen
        buf.writeBoolean(true);
        // Do limited crafting
        buf.writeBoolean(false);
    }

    // ================================================================
    //  1.20.5+ (protocol 766+)
    // ================================================================
    private void writeV1205(ByteBuf buf) {
        buf.writeInt(1);            // Entity ID
        buf.writeBoolean(false);    // Is Hardcore
        // Dimension names
        MinecraftTypes.writeVarInt(buf, 1);
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        // Max players
        MinecraftTypes.writeVarInt(buf, 1);
        // View distance
        MinecraftTypes.writeVarInt(buf, 8);
        // Simulation distance
        MinecraftTypes.writeVarInt(buf, 8);
        // Reduced debug info
        buf.writeBoolean(false);
        // Enable respawn screen
        buf.writeBoolean(true);
        // Do limited crafting
        buf.writeBoolean(false);
        // Dimension type (identifier)
        MinecraftTypes.writeString(buf, "minecraft:overworld");
        // Dimension ID (identifier — the specific dimension registry key)
        MinecraftTypes.writeString(buf, "minecraft:overworld");
    }
}
