package top.yzljc.limbo.world;

import com.viaversion.nbt.io.NBTIO;
import com.viaversion.nbt.limiter.TagLimiter;
import com.viaversion.nbt.tag.CompoundTag;
import com.viaversion.nbt.tag.ListTag;
import com.viaversion.nbt.tag.Tag;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import java.io.IOException;
import java.util.*;

/** Shared immutable 1.8 terrain template. No ticking, entities or persistence. */
public final class FakeWorld {
    public record ChunkPos(int x, int z) {}
    public record Sign(long position, List<String> lines) {
        public Sign {
            lines = List.copyOf(lines);
            if (lines.size() != 4) throw new IllegalArgumentException("A legacy sign needs four lines");
        }
    }
    private final Map<ChunkPos, short[][]> chunks;
    private final Map<Long, Sign> signs;

    private FakeWorld(Map<ChunkPos, short[][]> chunks, Map<Long, Sign> signs) {
        this.chunks = Map.copyOf(chunks);
        this.signs = Map.copyOf(signs);
    }
    public Collection<Sign> signs() { return signs.values(); }
    public Sign sign(long position) { return signs.get(position); }

    public static FakeWorld load(MapSettings settings) throws IOException {
        if (settings.file() == null) return cottage();
        CompoundTag root = NBTIO.reader(CompoundTag.class).named()
                .tagLimiter(TagLimiter.create(32 * 1024 * 1024, 32)).read(settings.file(), true);
        int w = root.getShort("Width") & 0xffff, h = root.getShort("Height") & 0xffff,
                l = root.getShort("Length") & 0xffff;
        if (!"Alpha".equals(root.getString("Materials", "Alpha")) || w < 1 || w > 128
                || l < 1 || l > 128 || h < 1 || h + settings.originY() > 256)
            throw new IOException("Expected legacy Alpha .schematic: width/length 1..128, world height 0..255");
        int size = w * h * l;
        var blocksTag = root.getByteArrayTag("Blocks");
        var dataTag = root.getByteArrayTag("Data");
        var addTag = root.getByteArrayTag("AddBlocks");
        if (blocksTag == null || dataTag == null || blocksTag.getValue().length != size
                || dataTag.getValue().length != size || (addTag != null && addTag.getValue().length != (size + 1) / 2))
            throw new IOException("Invalid schematic block arrays");
        byte[] blocks = blocksTag.getValue(), data = dataTag.getValue();
        byte[] add = addTag == null ? null : addTag.getValue();
        Builder builder = new Builder();
        for (int y = 0; y < h; y++) for (int z = 0; z < l; z++) for (int x = 0; x < w; x++) {
            int i = x + z * w + y * w * l;
            int id = blocks[i] & 255;
            if (add != null) id |= ((add[i / 2] >> ((i & 1) * 4)) & 15) << 8;
            if (id > 197 || (data[i] & 255) > 15)
                throw new IOException("Schematic contains a block unsupported by 1.8 at index " + i + ": " + id);
            builder.set(settings.originX() + x, settings.originY() + y, settings.originZ() + z,
                    (id << 4) | data[i]);
        }
        if (root.get("TileEntities") instanceof ListTag<?> entities) {
            for (Tag entry : entities) {
                if (!(entry instanceof CompoundTag entity)) continue;
                String id = entity.getString("id", "");
                if (!id.equals("Sign") && !id.equals("minecraft:sign")) continue;
                if (!entity.contains("x") || !entity.contains("y") || !entity.contains("z")) continue;
                int x = entity.getInt("x"), y = entity.getInt("y"), z = entity.getInt("z");
                if (x < 0 || x >= w || y < 0 || y >= h || z < 0 || z >= l) continue;
                int block = blocks[x + z * w + y * w * l] & 255;
                if (block != 63 && block != 68) continue; // ignore stale tile entities
                List<String> lines = new ArrayList<>(4);
                for (int line = 1; line <= 4; line++) lines.add(signLine(entity.getString("Text" + line, "")));
                long pos = position(settings.originX() + x, settings.originY() + y, settings.originZ() + z);
                builder.signs.put(pos, new Sign(pos, lines));
            }
        }
        return builder.build();
    }

    private static String signLine(String text) throws IOException {
        // Pre-1.8 schematics store plain text; 1.8+ stores JSON components.
        String result = new JsonPrimitive(text).toString();
        try {
            var component = JsonParser.parseString(text);
            if (component.isJsonNull()) result = "\"\""; // WorldEdit exports blank lines as JSON null.
            else if (component.isJsonPrimitive()
                    || component.isJsonArray() && !component.getAsJsonArray().isEmpty()
                    || component.isJsonObject() && (component.getAsJsonObject().has("text")
                    || component.getAsJsonObject().has("translate") || component.getAsJsonObject().has("score")
                    || component.getAsJsonObject().has("selector"))) result = component.toString();
        } catch (RuntimeException ignored) { /* A plain legacy line is not necessarily JSON. */ }
        if (result.length() > 32767) throw new IOException("Schematic sign text exceeds the protocol string limit");
        return result;
    }

    public int block(int x, int y, int z) {
        if (y < 0 || y > 255) return 0;
        short[][] sections = chunks.get(new ChunkPos(x >> 4, z >> 4));
        if (sections == null || sections[y >> 4] == null) return 0;
        return sections[y >> 4][((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] & 0xffff;
    }
    public Set<ChunkPos> initialChunks(MapSettings spawn, int radius) {
        Set<ChunkPos> result = new LinkedHashSet<>();
        int cx = ((int) Math.floor(spawn.spawnX())) >> 4, cz = ((int) Math.floor(spawn.spawnZ())) >> 4;
        for (int x = cx - radius; x <= cx + radius; x++)
            for (int z = cz - radius; z <= cz + radius; z++) result.add(new ChunkPos(x, z));
        result.addAll(chunks.keySet());
        return Collections.unmodifiableSet(result);
    }
    public int sectionMask(int x, int z) {
        short[][] sections = chunks.get(new ChunkPos(x, z));
        int mask = 0;
        if (sections != null) for (int i = 0; i < 16; i++) if (sections[i] != null) mask |= 1 << i;
        return mask == 0 ? 1 : mask; // 1.8 interprets a full chunk with mask 0 as unload
    }
    public static long position(int x, int y, int z) {
        return ((x & 0x3ffffffL) << 38) | ((y & 0xfffL) << 26) | (z & 0x3ffffffL);
    }
    public static int x(long pos) { return (int) (pos >> 38); }
    public static int y(long pos) { return (int) (pos << 26 >> 52); }
    public static int z(long pos) { return (int) (pos << 38 >> 38); }

    private static FakeWorld cottage() {
        Builder b = new Builder();
        b.fill(-12, 60, -12, 12, 61, 12, 1 << 4);
        b.fill(-12, 62, -12, 12, 62, 12, 3 << 4);
        b.fill(-12, 63, -12, 12, 63, 12, 2 << 4);
        b.fill(-1, 63, 0, 1, 63, 6, 4 << 4);
        b.fill(-4, 63, 4, 4, 63, 11, 5 << 4);
        b.fill(-4, 64, 4, 4, 67, 11, 5 << 4);
        b.fill(-3, 64, 5, 3, 66, 10, 0);
        for (int x : new int[]{-4, 4}) for (int z : new int[]{4, 11}) b.fill(x, 64, z, x, 67, z, 17 << 4);
        b.fill(0, 64, 4, 0, 65, 4, 0); // open doorway
        b.fill(-4, 65, 7, -4, 66, 8, 20 << 4);
        b.fill(4, 65, 7, 4, 66, 8, 20 << 4);
        b.fill(-1, 65, 11, 1, 66, 11, 20 << 4);
        b.fill(-5, 68, 3, 5, 68, 12, 5 << 4 | 1);
        b.fill(-4, 69, 4, 4, 69, 11, 5 << 4 | 1);
        b.fill(-2, 70, 5, 2, 70, 10, 5 << 4 | 1);
        b.set(-2, 66, 9, 89 << 4); // interior light
        b.fill(7, 64, -5, 7, 67, -5, 17 << 4);
        b.fill(5, 67, -7, 9, 69, -3, 18 << 4 | 4);
        b.fill(6, 70, -6, 8, 70, -4, 18 << 4 | 4);
        return b.build();
    }
    private static final class Builder {
        private final Map<ChunkPos, short[][]> chunks = new HashMap<>();
        private final Map<Long, Sign> signs = new HashMap<>();
        void set(int x, int y, int z, int state) {
            var sections = chunks.computeIfAbsent(new ChunkPos(x >> 4, z >> 4), ignored -> new short[16][]);
            if (sections[y >> 4] == null) sections[y >> 4] = new short[4096];
            sections[y >> 4][((y & 15) << 8) | ((z & 15) << 4) | (x & 15)] = (short) state;
        }
        void fill(int x1, int y1, int z1, int x2, int y2, int z2, int state) {
            for (int y = y1; y <= y2; y++) for (int z = z1; z <= z2; z++)
                for (int x = x1; x <= x2; x++) set(x, y, z, state);
        }
        FakeWorld build() { return new FakeWorld(chunks, signs); }
    }
}
