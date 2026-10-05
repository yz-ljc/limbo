package top.yzljc.limbo.protocol;

import com.google.gson.JsonObject;
import com.google.gson.JsonArray;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import top.yzljc.limbo.network.VarInts;
import top.yzljc.limbo.util.MinecraftTypes;
import top.yzljc.limbo.world.FakeWorld;
import top.yzljc.limbo.world.MapSettings;
import top.yzljc.limbo.world.PlayerInventory;
import top.yzljc.limbo.proxy.PlayerProfile;
import com.viaversion.viaversion.api.minecraft.item.Item;
import com.viaversion.viaversion.api.type.Types;
import java.util.UUID;
import java.util.function.Consumer;

/** The server speaks ONLY protocol 47. ViaVersion owns all newer wire formats. */
public final class LimboPackets {
    private LimboPackets() {}
    public static ByteBuf packet(int id, Consumer<ByteBuf> writer) {
        ByteBuf b = Unpooled.buffer();
        try {
            VarInts.write(b, id);
            writer.accept(b);
            return b;
        } catch (Throwable e) {
            b.release();
            throw e;
        }
    }
    public static String text(String text) {
        JsonObject json = new JsonObject();
        json.addProperty("text", text);
        return json.toString();
    }
    public static ByteBuf chat(String line) {
        // Use explicit component colors so modern clients never display literal section codes.
        String[] colors = {"black", "dark_blue", "dark_green", "dark_aqua", "dark_red", "dark_purple",
                "gold", "gray", "dark_gray", "blue", "green", "aqua", "red", "light_purple", "yellow", "white"};
        JsonObject component = new JsonObject();
        component.addProperty("text", "");
        JsonArray parts = new JsonArray();
        String color = "white";
        int start = 0;
        for (int i = 0; i <= line.length(); i++) {
            int code = i + 1 < line.length() && line.charAt(i) == '§'
                    ? Character.digit(line.charAt(i + 1), 16) : -1;
            if (i != line.length() && code < 0) continue;
            if (i > start) {
                JsonObject part = new JsonObject();
                part.addProperty("text", line.substring(start, i));
                part.addProperty("color", color);
                parts.add(part);
            }
            if (code >= 0) { color = colors[code]; i++; start = i + 1; }
        }
        if (!parts.isEmpty()) component.add("extra", parts);
        return packet(0x02, b -> {
            MinecraftTypes.writeString(b, component.toString());
            b.writeByte(0); // chat box; Via converts this to unsigned system chat on modern clients
        });
    }
    public static ByteBuf login(UUID uuid, String username) {
        return packet(2, b -> {
            MinecraftTypes.writeString(b, uuid.toString());
            MinecraftTypes.writeString(b, username);
        });
    }
    public static ByteBuf join() {
        return packet(1, b -> {
            b.writeInt(1);       // entity ID
            b.writeByte(0);      // survival: retain the normal inventory screen
            b.writeByte(1);      // fixed End dimension, no skylight arrays
            b.writeByte(0);      // peaceful
            b.writeByte(0);      // legacy max players (unused)
            MinecraftTypes.writeString(b, "flat");
            b.writeBoolean(false);
        });
    }
    public static ByteBuf brand(String brand) {
        return packet(0x3f, b -> {
            MinecraftTypes.writeString(b, "MC|Brand");
            MinecraftTypes.writeString(b, brand);
        });
    }
    public static ByteBuf playerInfo(UUID uuid, String username) {
        return playerInfo(new PlayerProfile(uuid, username, null, java.util.List.of()));
    }
    public static ByteBuf playerInfo(PlayerProfile profile) {
        return packet(0x38, b -> {
            VarInts.write(b, 0); // add player
            VarInts.write(b, 1);
            b.writeLong(profile.uuid().getMostSignificantBits());
            b.writeLong(profile.uuid().getLeastSignificantBits());
            MinecraftTypes.writeString(b, profile.username());
            VarInts.write(b, profile.properties().size());
            for (var property : profile.properties()) {
                MinecraftTypes.writeString(b, property.name());
                MinecraftTypes.writeString(b, property.value());
                b.writeBoolean(property.signature() != null);
                if (property.signature() != null) MinecraftTypes.writeString(b, property.signature());
            }
            VarInts.write(b, 0); // survival
            VarInts.write(b, 0); // latency
            b.writeBoolean(false);
        });
    }
    public static ByteBuf abilities(boolean legacyPick) {
        return packet(0x39, b -> {
            // Modern clients use instabuild to choose the creative inventory UI.
            // 1.21.4+ has a dedicated pick request; it does not need that ability.
            b.writeByte(legacyPick ? 0x09 : 0x01);
            b.writeFloat(0.05f);
            b.writeFloat(0.1f);
        });
    }
    public static ByteBuf skinParts(int parts) {
        return packet(0x1c, b -> {
            VarInts.write(b, 1); // own player entity ID
            b.writeByte(10); // 1.8 metadata index 10, type byte
            b.writeByte(parts & 0x7f); // cape, jacket, sleeves, trousers, hat
            b.writeByte(0x7f); // metadata terminator
        });
    }
    public static ByteBuf position(MapSettings spawn) {
        return packet(0x08, b -> {
            b.writeDouble(spawn.spawnX());
            b.writeDouble(spawn.spawnY());
            b.writeDouble(spawn.spawnZ());
            b.writeFloat(spawn.yaw());
            b.writeFloat(spawn.pitch());
            b.writeByte(0);
        });
    }
    public static ByteBuf spawn(MapSettings spawn) {
        return packet(0x05, b -> b.writeLong(FakeWorld.position((int) Math.floor(spawn.spawnX()),
                (int) Math.floor(spawn.spawnY()), (int) Math.floor(spawn.spawnZ()))));
    }
    public static ByteBuf time() {
        return packet(0x03, b -> { b.writeLong(0); b.writeLong(-6000); });
    }
    public static ByteBuf keepAlive(int id) {
        return packet(0, b -> VarInts.write(b, id));
    }
    public static ByteBuf disconnect(boolean play, String reason) {
        return packet(play ? 0x40 : 0, b -> MinecraftTypes.writeString(b, text(reason)));
    }
    public static ByteBuf slot(int slot, Item item) {
        return packet(0x2f, b -> {
            b.writeByte(0);
            b.writeShort(slot);
            Types.ITEM1_8.write(b, item);
        });
    }
    public static ByteBuf inventory(PlayerInventory inventory) {
        return packet(0x30, b -> {
            b.writeByte(0); b.writeShort(45);
            for (int i = 0; i < 45; i++) Types.ITEM1_8.write(b, inventory.get(i));
        });
    }
    public static ByteBuf cursor(Item item) {
        return packet(0x2f, b -> { b.writeByte(-1); b.writeShort(-1); Types.ITEM1_8.write(b, item); });
    }
    public static ByteBuf transaction(int window, short action) {
        return packet(0x32, b -> { b.writeByte(window); b.writeShort(action); b.writeBoolean(true); });
    }
    public static ByteBuf block(long position, int state) {
        return packet(0x23, b -> { b.writeLong(position); VarInts.write(b, state); });
    }
    public static ByteBuf sign(FakeWorld.Sign sign) {
        return packet(0x33, b -> {
            b.writeLong(sign.position());
            for (String line : sign.lines()) MinecraftTypes.writeString(b, line);
        });
    }
    public static ByteBuf chunk(FakeWorld world, int x, int z) {
        return packet(0x21, b -> {
            b.writeInt(x);
            b.writeInt(z);
            b.writeBoolean(true);
            int mask = world.sectionMask(x, z), count = Integer.bitCount(mask);
            b.writeShort(mask);
            VarInts.write(b, count * 10240 + 256);
            for (int section = 0; section < 16; section++) {
                if ((mask & (1 << section)) == 0) continue;
                for (int y = 0; y < 16; y++) for (int bz = 0; bz < 16; bz++) for (int bx = 0; bx < 16; bx++)
                    b.writeShortLE(world.block(x * 16 + bx, section * 16 + y, z * 16 + bz));
            }
            // The End carries block light only. Extra skylight bytes corrupt the biome data.
            for (int i = 0; i < count * 2048; i++) b.writeByte(0xff);
            for (int i = 0; i < 256; i++) b.writeByte(9); // Sky / The End in 1.8
        });
    }
}
