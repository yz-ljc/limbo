package top.yzljc.limbo.world;

import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Per-connection edits; disconnecting discards them. */
public final class PlayerWorld {
    private static final int MAX_EDITS = 8192;
    private final FakeWorld template;
    private final Set<FakeWorld.ChunkPos> loaded;
    private final Map<Long, Integer> edits = new HashMap<>();
    private final PlayerInventory inventory = new PlayerInventory();
    private int selected;
    private double playerX, playerY, playerZ;
    private float yaw;
    private double height = 1.8, eyeHeight = 1.62;
    private Long digging;
    private int diggingState;

    public PlayerWorld(FakeWorld template, MapSettings settings, int radius) {
        this.template = template;
        this.loaded = template.initialChunks(settings, radius);
        move(settings.spawnX(), settings.spawnY(), settings.spawnZ());
        yaw = settings.yaw();
    }
    public Set<FakeWorld.ChunkPos> chunks() { return loaded; }
    public int block(long position) {
        return edits.getOrDefault(position, template.block(FakeWorld.x(position), FakeWorld.y(position), FakeWorld.z(position)));
    }
    public boolean contains(long pos) {
        return FakeWorld.y(pos) >= 0 && FakeWorld.y(pos) < 256
                && loaded.contains(new FakeWorld.ChunkPos(FakeWorld.x(pos) >> 4, FakeWorld.z(pos) >> 4));
    }
    public void edit(long pos, int state) {
        if (!contains(pos)) return;
        int original = template.block(FakeWorld.x(pos), FakeWorld.y(pos), FakeWorld.z(pos));
        if (state == original) edits.remove(pos);
        else if (edits.containsKey(pos) || edits.size() < MAX_EDITS) edits.put(pos, state);
    }
    public int selected() { return selected; }
    public void select(int slot) {
        if (slot < 0 || slot > 8) throw new IllegalArgumentException("Invalid hotbar slot");
        if (selected != slot) digging = null;
        selected = slot;
    }
    public PlayerInventory inventory() { return inventory; }
    public Item held() { return inventory.get(36 + selected); }
    public void setSlot(int slot, Item item) { inventory.set(36 + slot, item); }
    public void move(double x, double y, double z) { playerX = x; playerY = y; playerZ = z; }
    public void look(float yaw) { this.yaw = yaw; }
    public void sneak(boolean sneaking, boolean modern) {
        height = sneaking && modern ? 1.5 : 1.8;
        eyeHeight = sneaking ? (modern ? 1.27 : 1.54) : 1.62;
    }
    public boolean inReach(long pos) {
        double dx = Math.max(FakeWorld.x(pos) - playerX, Math.max(0, playerX - FakeWorld.x(pos) - 1));
        double dy = Math.max(FakeWorld.y(pos) - playerY - eyeHeight, Math.max(0, playerY + eyeHeight - FakeWorld.y(pos) - 1));
        double dz = Math.max(FakeWorld.z(pos) - playerZ, Math.max(0, playerZ - FakeWorld.z(pos) - 1));
        return contains(pos) && dx * dx + dy * dy + dz * dz <= 4.5 * 4.5;
    }
    /** START/ABORT/STOP match the survival mining lifecycle. Client computes tool/hardness progress. */
    public boolean dig(int action, long pos) {
        if (action == 1) { digging = null; return false; }
        if (action == 0) {
            digging = null;
            if (!inReach(pos) || !BlockRules.breakable(block(pos))) return false;
            if (BlockRules.instant(block(pos))) { edit(pos, 0); return block(pos) == 0; }
            digging = pos;
            diggingState = block(pos);
            return false;
        }
        if (action == 2) {
            boolean valid = digging != null && digging == pos && inReach(pos) && block(pos) == diggingState;
            digging = null;
            if (valid) { edit(pos, 0); return block(pos) == 0; }
        }
        return false;
    }
    public void consumeOne() {
        inventory.consume(36 + selected, 1);
    }
    public record Placement(long target, boolean placed) {}
    public Placement place(long clicked, int face, float hitX, float hitY, float hitZ) {
        long adjacent = offset(clicked, face);
        long target = BlockRules.replaceable(block(clicked)) ? clicked : adjacent;
        Item held = held();
        if (face < 0 || face > 5 || !validHit(hitX) || !validHit(hitY) || !validHit(hitZ)
                || !inReach(clicked) || block(clicked) == 0 || held == null || held.amount() <= 0)
            return new Placement(target, false);
        double dx = FakeWorld.x(clicked) + hitX - playerX;
        double dy = FakeWorld.y(clicked) + hitY - playerY - eyeHeight;
        double dz = FakeWorld.z(clicked) + hitZ - playerZ;
        if (dx * dx + dy * dy + dz * dz > 4.6 * 4.6) return new Placement(target, false);
        int id = held.identifier();
        int state = placedState(held, face, hitY, yaw);
        boolean merging = false;
        if (BlockRules.slab(id)) {
            int clickedState = block(clicked);
            if ((clickedState >> 4) == id && (clickedState & 7) == (held.data() & 7)
                    && (face == 1 && (clickedState & 8) == 0 || face == 0 && (clickedState & 8) != 0)) {
                target = clicked;
                merging = true;
            } else if ((block(target) >> 4) == id && (block(target) & 7) == (held.data() & 7)) merging = true;
            if (merging) state = BlockRules.doubleSlab(id) << 4 | (held.data() & 7);
        }
        if (state == 0 || !contains(target) || !inReach(target)
                || (!merging && !BlockRules.replaceable(block(target)))
                || BlockRules.intersectsPlayer(state, target, playerX, playerY, playerZ, height))
            return new Placement(target, false);
        // Plants, carpet and torches require support. Complex multi-block items are not fabricated.
        if ((id == 6 || id == 37 || id == 38 || id == 171) && block(offset(target, 0)) == 0)
            return new Placement(target, false);
        edit(target, state);
        if (block(target) != state) return new Placement(target, false);
        consumeOne();
        return new Placement(target, true);
    }
    private static boolean validHit(float n) { return Float.isFinite(n) && n >= 0 && n <= 1; }
    public static long offset(long pos, int face) {
        int x = FakeWorld.x(pos), y = FakeWorld.y(pos), z = FakeWorld.z(pos);
        return switch (face) {
            case 0 -> FakeWorld.position(x, y - 1, z);
            case 1 -> FakeWorld.position(x, y + 1, z);
            case 2 -> FakeWorld.position(x, y, z - 1);
            case 3 -> FakeWorld.position(x, y, z + 1);
            case 4 -> FakeWorld.position(x - 1, y, z);
            case 5 -> FakeWorld.position(x + 1, y, z);
            default -> pos;
        };
    }
    public Item pick(long pos) {
        if (!inReach(pos)) return null;
        Item picked = itemForBlock(block(pos));
        if (picked == null) return null;
        inventory.pick(36 + selected, picked);
        return held();
    }
    public static Item cleanItem(Item item) {
        if (item == null || item.amount() <= 0 || item.identifier() < 1 || item.identifier() > 431) return null;
        // Scenery items carry no arbitrary client NBT or inventories.
        return new DataItem(item.identifier(), (byte) Math.min(64, item.amount()), (short) Math.max(0, item.data()), null);
    }
    public static Item itemForBlock(int state) {
        int id = state >> 4, data = state & 15;
        if (id == 0) return null;
        int item = switch (id) {
            case 8, 9 -> 326;
            case 10, 11 -> 327;
            case 26 -> 355;
            case 43 -> 44;
            case 55 -> 331;
            case 59 -> 295;
            case 60 -> 3;
            case 62 -> 61;
            case 63, 68 -> 323;
            case 64 -> 324;
            case 71 -> 330;
            case 74 -> 73;
            case 75 -> 76;
            case 93, 94 -> 356;
            case 104 -> 361;
            case 105 -> 362;
            case 115 -> 372;
            case 117 -> 379;
            case 118 -> 380;
            case 124 -> 123;
            case 125 -> 126;
            case 127 -> 351;
            case 140 -> 390;
            case 141 -> 391;
            case 142 -> 392;
            case 144 -> 397;
            case 149, 150 -> 404;
            case 176, 177 -> 425;
            case 178 -> 151;
            case 181 -> 182;
            case 193, 194, 195, 196, 197 -> id + 234;
            default -> id;
        };
        data = switch (id) {
            case 1, 3, 5, 6, 12, 19, 35, 38, 95, 97, 98, 139, 159, 160, 168, 171, 175 -> data;
            case 17, 18, 161, 162 -> data & 3;
            case 43, 44, 125, 126 -> data & 7;
            case 127 -> 3;
            default -> 0;
        };
        return new DataItem(item, (byte) 1, (short) data, null);
    }
    public static int placedState(Item item, int face, float hitY, float yaw) {
        if (item == null || item.identifier() < 1 || item.identifier() > 197) return 0;
        int id = item.identifier(), data = item.data() & 15;
        if (id == 17 || id == 162) data = (data & 3) | (face < 2 ? 0 : face < 4 ? 8 : 4);
        if (id == 18 || id == 161) data = (data & 3) | 4;
        if (BlockRules.slab(id)) data = (data & 7) | (face == 0 || face > 1 && hitY > 0.5f ? 8 : 0);
        if (BlockRules.stairs(id)) {
            int facing = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
            data = new int[]{2, 1, 3, 0}[facing] | (face == 0 || face > 1 && hitY > 0.5f ? 4 : 0);
        }
        if (id == 50 || id == 76) {
            if (face == 0) return 0;
            data = new int[]{0, 5, 4, 3, 2, 1}[face];
        }
        if (id == 23 || id == 54 || id == 61 || id == 130 || id == 146 || id == 158) {
            int facing = Math.floorMod((int) Math.floor(yaw / 90.0 + 0.5), 4);
            data = new int[]{2, 5, 3, 4}[facing];
        }
        if (id == 26 || id == 64 || id == 71 || id == 175 || id >= 193) return 0;
        return id << 4 | data;
    }
}
