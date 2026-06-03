package top.yzljc.limbo.protocol.packet.clientbound.configuration;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.RegistryData;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Sends registry tags to the client during CONFIGURATION state.
 * <p>
 * Packet 0x0D in configuration phase (1.20.5+).
 * Tags reference registry elements by their 0-based index
 * (matching the order sent via {@link ConfigurationRegistryDataPacket}).
 * </p>
 */
public class ClientboundUpdateTagsPacket implements ClientboundPacket {

    private final int protocolVersion;
    private final Collection<RegistryData.RegistryDef> registries;

    public ClientboundUpdateTagsPacket(int protocolVersion,
                                       Collection<RegistryData.RegistryDef> registries) {
        this.protocolVersion = protocolVersion;
        this.registries = registries;
    }

    @Override
    public int packetId() {
        if (protocolVersion >= 766) return 0x0D; // 1.20.5+
        return 0x07; // 1.20.2-1.20.4
    }

    @Override
    public void write(ByteBuf buf) {
        MinecraftTypes.writeVarInt(buf, registries.size());

        for (RegistryData.RegistryDef registry : registries) {
            MinecraftTypes.writeString(buf, registry.key());

            Map<String, List<Integer>> tags = registry.tags();
            MinecraftTypes.writeVarInt(buf, tags.size());

            for (Map.Entry<String, List<Integer>> tagEntry : tags.entrySet()) {
                MinecraftTypes.writeString(buf, tagEntry.getKey());
                List<Integer> indices = tagEntry.getValue();
                MinecraftTypes.writeVarInt(buf, indices.size());
                for (int idx : indices) {
                    MinecraftTypes.writeVarInt(buf, idx);
                }
            }
        }
    }
}
