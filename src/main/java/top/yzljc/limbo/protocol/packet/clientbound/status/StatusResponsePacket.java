package top.yzljc.limbo.protocol.packet.clientbound.status;

import io.netty.buffer.ByteBuf;
import top.yzljc.limbo.protocol.packet.clientbound.ClientboundPacket;
import top.yzljc.limbo.util.MinecraftTypes;

public class StatusResponsePacket implements ClientboundPacket {

    private final String jsonResponse;

    public StatusResponsePacket(String jsonResponse) {
        this.jsonResponse = jsonResponse;
    }

    @Override
    public int packetId() {
        return 0x00;
    }

    @Override
    public void write(ByteBuf buf) {
        MinecraftTypes.writeString(buf, jsonResponse);
    }
}

