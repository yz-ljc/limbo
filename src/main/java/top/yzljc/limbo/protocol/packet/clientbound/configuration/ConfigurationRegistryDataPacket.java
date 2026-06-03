package top.yzljc.limbo.protocol.packet.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import net.querz.nbt.tag.CompoundTag;
import top.yzljc.limbo.protocol.RegistryData;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

import java.util.Map;

/**
 * Sends a single registry's elements to the client during the CONFIGURATION state.
 * <p>
 * For 1.20.5+ (protocol 766+):
 * <ol>
 *   <li>Registry identifier (String)</li>
 *   <li>Entry count (VarInt)</li>
 *   <li>For each entry: entry ID (String), has-data (Boolean), NBT data</li>
 * </ol>
 * <b>Tags are NOT included.</b> In 1.20.5+, the client generates tags internally
 * from the element data. Sending tag entries mixed with elements would cause
 * codec parse failures.
 * </p>
 */
public class ConfigurationRegistryDataPacket implements ClientboundPacket {

    private final int protocolVersion;
    private final RegistryData.RegistryDef registry;

    public ConfigurationRegistryDataPacket(int protocolVersion, RegistryData.RegistryDef registry) {
        this.protocolVersion = protocolVersion;
        this.registry = registry;
    }

    @Override
    public int packetId() {
        if (protocolVersion >= 766) return 0x07; // 1.20.5+
        return 0x05; // 1.20.2–1.20.4
    }

    @Override
    public void write(ByteBuf buf) {
        // Registry identifier
        MinecraftTypes.writeString(buf, registry.key());

        // Entry count
        Map<String, CompoundTag> entries = registry.entries();
        MinecraftTypes.writeVarInt(buf, entries.size());

        // Each entry
        for (Map.Entry<String, CompoundTag> entry : entries.entrySet()) {
            MinecraftTypes.writeString(buf, entry.getKey());
            CompoundTag data = entry.getValue();
            if (data == null) {
                buf.writeBoolean(false);
            } else {
                buf.writeBoolean(true);
                MinecraftTypes.writeNbt(buf, data);
            }
        }
    }
}
