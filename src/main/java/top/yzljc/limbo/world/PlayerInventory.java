package top.yzljc.limbo.world;

import com.viaversion.viaversion.api.minecraft.item.Item;
import java.util.LinkedHashSet;
import java.util.Set;

/** Window 0: result, 2x2 crafting, armour, storage and hotbar; owned by one connection. */
public final class PlayerInventory {
    private final Item[] slots = new Item[45];
    private Item cursor;
    private final Set<Integer> dragSlots = new LinkedHashSet<>();
    private int dragMode = -1;

    public Item get(int slot) { return slot >= 0 && slot < slots.length ? slots[slot] : null; }
    public Item cursor() { return cursor; }
    public void set(int slot, Item item) {
        if (slot < 1 || slot >= slots.length) return;
        slots[slot] = PlayerWorld.cleanItem(item);
        if (slots[slot] != null) slots[slot].setAmount(Math.min(slots[slot].amount(), limit(slot, slots[slot])));
        refresh();
    }
    public void consume(int slot, int count) {
        Item item = get(slot);
        if (item != null) {
            if (item.amount() <= count) slots[slot] = null;
            else item.setAmount(item.amount() - count);
        }
        refresh();
    }
    private static boolean same(Item a, Item b) {
        return a != null && b != null && a.identifier() == b.identifier() && a.data() == b.data();
    }
    public void pick(int slot, Item item) {
        // Selecting an already held stack must never replace it with a single item.
        if (!same(get(slot), item)) set(slot, item);
    }
    private static int limit(int slot, Item item) { return slot >= 5 && slot <= 8 ? 1 : CraftingRecipes.stackSize(item.identifier()); }
    private static boolean accepts(int slot, Item item) {
        if (slot < 1 || slot > 44) return false;
        if (item == null || slot < 5 || slot > 8) return true;
        int id = item.identifier();
        if (slot == 5 && (id == 86 || id == 397)) return true;
        return id >= 298 && id <= 317 && (id - 298) % 4 == slot - 5;
    }
    private void refresh() { slots[0] = CraftingRecipes.result(slots); }
    private void consumeRecipe() {
        for (int i = 1; i <= 4; i++) {
            if (slots[i] == null) continue;
            if (slots[i].amount() == 1) slots[i] = null;
            else slots[i].setAmount(slots[i].amount() - 1);
        }
        refresh();
    }
    public void click(int slot, int button, int mode) {
        if (mode != 5) { dragSlots.clear(); dragMode = -1; }
        if (mode == 0 && (button == 0 || button == 1)) {
            if (slot == -999) {
                if (cursor != null) {
                    if (button == 0 || cursor.amount() == 1) cursor = null;
                    else cursor.setAmount(cursor.amount() - 1);
                }
            } else if (slot == 0) takeResult(false);
            else if (slot >= 1 && slot <= 44) {
                Item current = slots[slot];
                if (cursor == null && current != null) {
                    int take = button == 0 ? current.amount() : (current.amount() + 1) / 2;
                    cursor = current.copy(); cursor.setAmount(take); consume(slot, take);
                } else if (cursor != null && accepts(slot, cursor)) {
                    if (current == null || same(current, cursor)) {
                        int room = limit(slot, cursor) - (current == null ? 0 : current.amount());
                        int move = Math.min(room, button == 0 ? cursor.amount() : 1);
                        if (move > 0) {
                            if (current == null) { slots[slot] = cursor.copy(); slots[slot].setAmount(move); }
                            else current.setAmount(current.amount() + move);
                            cursor.setAmount(cursor.amount() - move);
                            if (cursor.amount() == 0) cursor = null;
                        }
                    } else if (cursor.amount() <= limit(slot, cursor)) { slots[slot] = cursor; cursor = current; }
                }
            }
        } else if (mode == 1 && slot >= 0 && slot <= 44) {
            if (slot == 0) takeResult(true);
            else if (slots[slot] != null) {
                Item moving = slots[slot];
                insert(moving, slot >= 9 && slot <= 35 ? 36 : 9, slot >= 36 ? 36 : 45);
                if (moving.amount() == 0) slots[slot] = null;
            }
        } else if (mode == 2 && button >= 0 && button <= 8 && slot >= 0 && slot < 45) {
            int hotbar = 36 + button;
            if (slot == 0) {
                Item result = slots[0], current = slots[hotbar];
                if (result != null && (current == null || same(result, current))
                        && result.amount() + (current == null ? 0 : current.amount()) <= CraftingRecipes.stackSize(result.identifier())) {
                    if (current == null) slots[hotbar] = result.copy(); else current.setAmount(current.amount() + result.amount());
                    consumeRecipe();
                }
            } else if (accepts(slot, slots[hotbar]) && (slots[hotbar] == null || slots[hotbar].amount() <= limit(slot, slots[hotbar]))) {
                Item swap = slots[slot]; slots[slot] = slots[hotbar]; slots[hotbar] = swap;
            }
        } else if (mode == 4 && slot >= 1 && slot <= 44 && (button == 0 || button == 1)) {
            consume(slot, button == 0 ? 1 : 64);
        } else if (mode == 5) drag(slot, button);
        else if (mode == 6 && button == 0 && cursor != null) {
            for (int i = 1; i < 45 && cursor.amount() < CraftingRecipes.stackSize(cursor.identifier()); i++) {
                if (!same(cursor, slots[i])) continue;
                int n = Math.min(slots[i].amount(), CraftingRecipes.stackSize(cursor.identifier()) - cursor.amount());
                cursor.setAmount(cursor.amount() + n); consume(i, n);
            }
        }
        refresh();
    }
    private void takeResult(boolean shift) {
        for (int iteration = 0; iteration < (shift ? 256 : 1); iteration++) {
            Item result = slots[0];
            if (result == null) return;
            if (shift) {
                if (capacity(result, 9, 45) < result.amount()) return;
                insert(result.copy(), 9, 45);
            } else {
                if (cursor != null && (!same(cursor, result) || cursor.amount() + result.amount() > CraftingRecipes.stackSize(result.identifier()))) return;
                if (cursor == null) cursor = result.copy(); else cursor.setAmount(cursor.amount() + result.amount());
            }
            consumeRecipe();
        }
    }
    private int capacity(Item item, int from, int to) {
        int total = 0;
        for (int i = from; i < to; i++) {
            if (slots[i] == null) total += CraftingRecipes.stackSize(item.identifier());
            else if (same(slots[i], item)) total += CraftingRecipes.stackSize(item.identifier()) - slots[i].amount();
        }
        return total;
    }
    private void insert(Item item, int from, int to) {
        for (int pass = 0; pass < 2; pass++) for (int i = from; i < to && item.amount() > 0; i++) {
            Item current = slots[i];
            if (pass == 0 ? !same(current, item) : current != null) continue;
            int n = Math.min(item.amount(), CraftingRecipes.stackSize(item.identifier()) - (current == null ? 0 : current.amount()));
            if (n <= 0) continue;
            if (current == null) { slots[i] = item.copy(); slots[i].setAmount(n); }
            else current.setAmount(current.amount() + n);
            item.setAmount(item.amount() - n);
        }
    }
    private void drag(int slot, int button) {
        int phase = button & 3, type = button >> 2;
        if (type > 1) return; // creative fill is intentionally unsupported
        if (phase == 0) { dragSlots.clear(); dragMode = type; }
        else if (phase == 1 && dragMode == type && cursor != null && accepts(slot, cursor)
                && (get(slot) == null || same(get(slot), cursor))) dragSlots.add(slot);
        else if (phase == 2) {
            if (dragMode == type && cursor != null && !dragSlots.isEmpty()) {
                int each = type == 1 ? 1 : cursor.amount() / dragSlots.size();
                for (int dest : dragSlots) {
                    Item current = slots[dest];
                    int n = Math.min(cursor.amount(), Math.min(each, limit(dest, cursor) - (current == null ? 0 : current.amount())));
                    if (n <= 0) continue;
                    if (current == null) { slots[dest] = cursor.copy(); slots[dest].setAmount(n); }
                    else current.setAmount(current.amount() + n);
                    cursor.setAmount(cursor.amount() - n);
                }
                if (cursor.amount() == 0) cursor = null;
            }
            dragSlots.clear(); dragMode = -1;
        }
    }
    public void close() {
        for (int i = 1; i <= 4; i++) {
            if (slots[i] != null) insert(slots[i], 9, 45);
            slots[i] = null;
        }
        if (cursor != null) insert(cursor, 9, 45);
        cursor = null; dragSlots.clear(); dragMode = -1;
        refresh();
    }
}
