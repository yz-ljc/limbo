package top.yzljc.limbo.util;

import io.netty.buffer.ByteBuf;
import net.querz.nbt.io.NBTOutputStream;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.EndTag;
import net.querz.nbt.tag.Tag;
import top.yzljc.limbo.network.VarInts;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public final class MinecraftTypes {

    private MinecraftTypes() {
    }

    public static String readString(ByteBuf buf) {
        int length = VarInts.read(buf);
        byte[] bytes = new byte[length];
        buf.readBytes(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        VarInts.write(buf, value);
    }

    public static void writeUuid(ByteBuf buf, UUID uuid) {
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        VarInts.write(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    // ========================================================================
    //  NBT writing via querz library
    //
    //  Uses net.querz.nbt for serialization, which produces byte-for-byte
    //  identical output to Minecraft's NbtIo.writeAnyTag() format.
    // ========================================================================

    /**
     * Write a compound NBT tag in the exact format expected by
     * {@code FriendlyByteBuf.writeNbt()} / {@code NbtIo.writeAnyTag()}:
     * <pre>
     *   type_byte (0x0A)
     *   tag_data  (compound payload — NO root name)
     * </pre>
     */
    public static void writeNbt(ByteBuf buf, CompoundTag nbt) {
        try {
            // Write type byte
            buf.writeByte(nbt.getID());
            // Write tag data via querz (matches Minecraft's NBT format)
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream dos = new DataOutputStream(baos);
            new NBTOutputStream(dos).writeRawTag(nbt, Tag.DEFAULT_MAX_DEPTH);
            buf.writeBytes(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to write NBT", e);
        }
    }
}
