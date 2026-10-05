package top.yzljc.limbo.world;

import com.google.gson.*;
import com.viaversion.viaversion.api.minecraft.item.DataItem;
import com.viaversion.viaversion.api.minecraft.item.Item;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** The 1.8 recipe subset that fits the player's two-by-two grid. */
final class CraftingRecipes {
    private record Ingredient(int id, int data) {
        boolean matches(Item item) { return item != null && item.identifier() == id && (data < 0 || item.data() == data); }
    }
    private record Recipe(Ingredient[][] shape, List<Ingredient> ingredients, Item result) {}
    private static final List<Recipe> RECIPES = new ArrayList<>();
    private static final Map<Integer, Integer> STACKS = new HashMap<>();
    static {
        try (var stream = Objects.requireNonNull(CraftingRecipes.class.getResourceAsStream("/world/crafting-1.8.json"));
             var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            var root = JsonParser.parseReader(reader).getAsJsonObject();
            root.getAsJsonObject("stackSizes").entrySet().forEach(e -> STACKS.put(Integer.parseInt(e.getKey()), e.getValue().getAsInt()));
            for (var entry : root.getAsJsonArray("recipes")) {
                var recipe = entry.getAsJsonObject();
                Ingredient[][] shape = null;
                List<Ingredient> ingredients = new ArrayList<>();
                if (recipe.has("inShape")) {
                    var rows = recipe.getAsJsonArray("inShape");
                    shape = new Ingredient[rows.size()][];
                    for (int y = 0; y < rows.size(); y++) {
                        var row = rows.get(y).getAsJsonArray();
                        shape[y] = new Ingredient[row.size()];
                        for (int x = 0; x < row.size(); x++) shape[y][x] = ingredient(row.get(x));
                    }
                } else for (var ing : recipe.getAsJsonArray("ingredients")) ingredients.add(ingredient(ing));
                var result = recipe.getAsJsonObject("result");
                RECIPES.add(new Recipe(shape, ingredients, new DataItem(result.get("id").getAsInt(),
                        result.get("count").getAsByte(), (short) (result.has("metadata") ? result.get("metadata").getAsInt() : 0), null)));
            }
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static int stackSize(int id) { return STACKS.getOrDefault(id, 64); }
    private static Ingredient ingredient(JsonElement e) {
        if (e == null || e.isJsonNull()) return null;
        if (e.isJsonPrimitive()) return e.getAsInt() < 0 ? null : new Ingredient(e.getAsInt(), -1);
        var o = e.getAsJsonObject();
        return new Ingredient(o.get("id").getAsInt(), o.has("metadata") ? o.get("metadata").getAsInt() : -1);
    }
    static Item result(Item[] slots) {
        // Legacy datasets describe these metadata-dependent recipes with a generic result.
        List<Item> occupied = new ArrayList<>();
        for (int i = 1; i <= 4; i++) if (slots[i] != null) occupied.add(slots[i]);
        if (occupied.size() == 2) {
            Item wool = occupied.stream().filter(i -> i.identifier() == 35).findFirst().orElse(null);
            Item dye = occupied.stream().filter(i -> i.identifier() == 351).findFirst().orElse(null);
            if (wool != null && dye != null && dye.data() <= 15)
                return new DataItem(35, (byte) 1, (short) (15 - dye.data()), null);
        }
        for (Recipe r : RECIPES) {
            if (r.shape != null) {
                int h = r.shape.length, w = r.shape[0].length;
                for (int y = 0; y <= 2 - h; y++) for (int x = 0; x <= 2 - w; x++)
                    for (boolean mirror : new boolean[]{false, true}) {
                        boolean match = true;
                        for (int gy = 0; gy < 2; gy++) for (int gx = 0; gx < 2; gx++) {
                            int sx = gx - x, sy = gy - y;
                            Ingredient ing = sx >= 0 && sx < w && sy >= 0 && sy < h
                                    ? r.shape[sy][mirror ? w - 1 - sx : sx] : null;
                            Item actual = slots[1 + gy * 2 + gx];
                            if (ing == null ? actual != null : !ing.matches(actual)) match = false;
                        }
                        if (match) return r.result.copy();
                    }
            } else if (occupied.size() == r.ingredients.size() && shapeless(r.ingredients, occupied, new boolean[occupied.size()], 0))
                return r.result.copy();
        }
        return null;
    }
    private static boolean shapeless(List<Ingredient> ingredients, List<Item> items, boolean[] used, int index) {
        if (index == ingredients.size()) return true;
        for (int i = 0; i < items.size(); i++) if (!used[i] && ingredients.get(index).matches(items.get(i))) {
            used[i] = true;
            if (shapeless(ingredients, items, used, index + 1)) return true;
            used[i] = false;
        }
        return false;
    }
}
