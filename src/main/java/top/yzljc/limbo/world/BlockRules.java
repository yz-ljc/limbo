package top.yzljc.limbo.world;

import com.google.gson.*;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Legacy block geometry shared by all translated clients. */
final class BlockRules {
    private record Block(boolean diggable, double hardness, double[][][] shapes) {}
    private static final Map<Integer, Block> BLOCKS = load();
    private static Map<Integer, Block> load() {
        try (var stream = Objects.requireNonNull(BlockRules.class.getResourceAsStream("/world/blocks-1.8.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            var shapes = root.getAsJsonObject("shapes");
            Map<Integer, Block> blocks = new HashMap<>();
            Gson gson = new Gson();
            for (var entry : root.getAsJsonArray("blocks")) {
                var b = entry.getAsJsonObject();
                double[][][] boxes = new double[16][][];
                var indices = b.get("shapes");
                for (int i = 0; i < 16; i++) {
                    int shape = indices == null ? 1 : indices.isJsonArray()
                            ? indices.getAsJsonArray().get(i % indices.getAsJsonArray().size()).getAsInt() : indices.getAsInt();
                    boxes[i] = gson.fromJson(shapes.get(Integer.toString(shape)), double[][].class);
                }
                blocks.put(b.get("id").getAsInt(), new Block(b.get("diggable").getAsBoolean(),
                        b.get("hardness").isJsonNull() ? -1 : b.get("hardness").getAsDouble(), boxes));
            }
            return Map.copyOf(blocks);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static boolean breakable(int state) {
        Block b = BLOCKS.get(state >> 4);
        return b != null && b.diggable && b.hardness >= 0;
    }
    static boolean instant(int state) {
        Block b = BLOCKS.get(state >> 4);
        return b != null && b.diggable && b.hardness == 0;
    }
    static boolean replaceable(int state) {
        return switch (state >> 4) {
            case 0, 8, 9, 10, 11, 31, 32, 51, 106 -> true;
            case 78 -> (state & 7) == 0;
            default -> false;
        };
    }
    static boolean slab(int id) { return id == 44 || id == 126 || id == 182; }
    static int doubleSlab(int id) { return id == 44 ? 43 : id == 126 ? 125 : 181; }
    static boolean stairs(int id) {
        return switch (id) {
            case 53, 67, 108, 109, 114, 128, 134, 135, 136, 156, 163, 164, 180 -> true;
            default -> false;
        };
    }
    static boolean intersectsPlayer(int state, long pos, double px, double py, double pz, double height) {
        Block b = BLOCKS.get(state >> 4);
        if (b == null) return true;
        int x = FakeWorld.x(pos), y = FakeWorld.y(pos), z = FakeWorld.z(pos);
        for (double[] box : b.shapes[state & 15]) {
            if (px + 0.3 > x + box[0] + 1e-7 && px - 0.3 < x + box[3] - 1e-7
                    && py + height > y + box[1] + 1e-7 && py < y + box[4] - 1e-7
                    && pz + 0.3 > z + box[2] + 1e-7 && pz - 0.3 < z + box[5] - 1e-7) return true;
        }
        return false;
    }
}
