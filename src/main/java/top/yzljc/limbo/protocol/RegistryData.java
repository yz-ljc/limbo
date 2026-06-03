package top.yzljc.limbo.protocol;

import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import top.yzljc.limbo.LimboServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads registry entries and tags from vanilla Minecraft JSON data files.
 */
public final class RegistryData {

    private RegistryData() {
    }

    public static final List<String> REGISTRY_ORDER = List.of(
            "worldgen/biome",
            "chat_type",
            "trim_pattern",
            "trim_material",
            "wolf_variant",
            "wolf_sound_variant",
            "pig_variant",
            "pig_sound_variant",
            "frog_variant",
            "cat_variant",
            "cat_sound_variant",
            "cow_sound_variant",
            "cow_variant",
            "chicken_sound_variant",
            "chicken_variant",
            "zombie_nautilus_variant",
            "painting_variant",
            "dimension_type",
            "damage_type",
            "banner_pattern",
            "enchantment",
            "jukebox_song",
            "instrument"
    );

    /**
     * A registry: its identifier, entries (name → NBT), and tags (tag name → element indices).
     * Element entries maintain insertion order for index-based tag resolution.
     */
    public static class RegistryDef {
        private final String key;
        private final LinkedHashMap<String, CompoundTag> entries;
        private final Map<String, List<Integer>> tags;
        private final Map<String, List<String>> rawTags; // tag name → raw values (for recursive resolution)

        RegistryDef(String key, LinkedHashMap<String, CompoundTag> entries,
                    Map<String, List<String>> rawTags) {
            this.key = key;
            this.entries = entries;
            this.rawTags = rawTags;
            this.tags = new LinkedHashMap<>();
        }

        public String key() { return key; }
        public Map<String, CompoundTag> entries() { return entries; }
        public Map<String, List<Integer>> tags() { return tags; }

        /** Resolve raw tag values to element indices (must be called after all registries loaded). */
        void resolveTags() {
            for (var entry : rawTags.entrySet()) {
                String tagName = entry.getKey();
                List<String> rawValues = entry.getValue();
                List<Integer> indices = new ArrayList<>();
                for (String raw : rawValues) {
                    if (raw.startsWith("#")) {
                        // Tag reference: resolve recursively
                        String refName = raw.substring(1);
                        List<Integer> resolved = resolveTagRef(refName);
                        if (resolved != null) {
                            indices.addAll(resolved);
                        }
                    } else {
                        // Direct element reference: find index in entries
                        int idx = findEntryIndex(raw);
                        if (idx >= 0) {
                            indices.add(idx);
                        }
                    }
                }
                tags.put(tagName, indices);
            }
        }

        private List<Integer> resolveTagRef(String refName) {
            List<String> refValues = rawTags.get(refName);
            if (refValues == null) return null;
            List<Integer> resolved = new ArrayList<>();
            for (String raw : refValues) {
                if (raw.startsWith("#")) {
                    List<Integer> sub = resolveTagRef(raw.substring(1));
                    if (sub != null) resolved.addAll(sub);
                } else {
                    int idx = findEntryIndex(raw);
                    if (idx >= 0) resolved.add(idx);
                }
            }
            return resolved;
        }

        private int findEntryIndex(String name) {
            int i = 0;
            for (String key : entries.keySet()) {
                if (key.equals(name)) return i;
                i++;
            }
            return -1;
        }
    }

    // ---- cache --------------------------------------------------------------

    private static final Map<String, RegistryDef> CACHE = new LinkedHashMap<>();
    private static volatile boolean loaded = false;

    public static Collection<RegistryDef> getRegistries() {
        ensureLoaded();
        return CACHE.values();
    }

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (CACHE) {
            if (loaded) return;
            // Phase 1: load all registry elements
            for (String key : REGISTRY_ORDER) {
                CACHE.put(key, loadRegistry(key));
            }
            // Phase 2: resolve tag indices (requires all entries loaded)
            for (RegistryDef def : CACHE.values()) {
                def.resolveTags();
            }
            loaded = true;
        }
    }

    // ---- loading ------------------------------------------------------------

    private static RegistryDef loadRegistry(String registryPath) {
        LinkedHashMap<String, CompoundTag> entries = new LinkedHashMap<>();
        Map<String, List<String>> rawTags = new LinkedHashMap<>();

        // Load element JSONs
        String elemPath = "data/minecraft/" + registryPath + "/";
        Pattern elemPattern = Pattern.compile(Pattern.quote(elemPath) + ".*\\.json$");
        for (String resourcePath : ClasspathResourceScanner.getResources(elemPattern)) {
            String relative = resourcePath.substring("data/minecraft/".length());
            int slashIdx = relative.indexOf('/');
            String namePart = relative.substring(slashIdx + 1, relative.lastIndexOf('.'));
            String entryKey = "minecraft:" + namePart;

            try (InputStream in = LimboServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) continue;
                JSONObject json = (JSONObject) new JSONParser().parse(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                entries.put(entryKey, jsonToNbt(json));
            } catch (Exception e) {
                System.err.println("Failed to load registry entry: " + resourcePath + " — " + e.getMessage());
            }
        }

        // Load tag JSONs
        String tagPath = "data/minecraft/tags/" + registryPath + "/";
        Pattern tagPattern = Pattern.compile(Pattern.quote(tagPath) + ".*\\.json$");
        for (String resourcePath : ClasspathResourceScanner.getResources(tagPattern)) {
            int lastSlash = resourcePath.lastIndexOf('/');
            int lastDot = resourcePath.lastIndexOf('.');
            String tagName = "minecraft:" + resourcePath.substring(lastSlash + 1, lastDot);

            try (InputStream in = LimboServer.class.getClassLoader().getResourceAsStream(resourcePath)) {
                if (in == null) continue;
                JSONObject json = (JSONObject) new JSONParser().parse(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                JSONArray values = (JSONArray) json.get("values");
                if (values != null) {
                    List<String> rawValues = new ArrayList<>();
                    for (Object v : values) {
                        rawValues.add((String) v);
                    }
                    rawTags.put(tagName, rawValues);
                }
            } catch (Exception e) {
                System.err.println("Failed to load tag: " + resourcePath + " — " + e.getMessage());
            }
        }

        return new RegistryDef("minecraft:" + registryPath, entries, rawTags);
    }

    // ---- JSON → NBT conversion ----------------------------------------------

    @SuppressWarnings("unchecked")
    static CompoundTag jsonToNbt(JSONObject json) {
        CompoundTag tag = new CompoundTag();
        for (Object obj : json.keySet()) {
            String key = (String) obj;
            Object rawValue = json.get(key);
            if (rawValue instanceof JSONObject) {
                tag.put(key, jsonToNbt((JSONObject) rawValue));
            } else if (rawValue instanceof JSONArray) {
                tag.put(key, jsonToList((JSONArray) rawValue));
            } else if (rawValue instanceof Boolean b) {
                tag.putBoolean(key, b);
            } else if (rawValue instanceof Long l) {
                tag.putLong(key, l);
            } else if (rawValue instanceof Double d) {
                double val = d;
                if (val == Math.floor(val) && val <= Integer.MAX_VALUE && val >= Integer.MIN_VALUE) {
                    tag.putInt(key, (int) val);
                } else {
                    tag.putDouble(key, val);
                }
            } else if (rawValue instanceof String s) {
                tag.putString(key, s);
            }
        }
        return tag;
    }

    @SuppressWarnings("unchecked")
    static ListTag<?> jsonToList(JSONArray json) {
        if (json.isEmpty()) {
            return ListTag.createUnchecked(null);
        }
        ListTag<?> listTag = ListTag.createUnchecked(null);
        for (Object rawValue : json) {
            if (rawValue instanceof JSONObject) {
                listTag.addUnchecked(jsonToNbt((JSONObject) rawValue));
            } else if (rawValue instanceof JSONArray) {
                listTag.addUnchecked(jsonToList((JSONArray) rawValue));
            } else if (rawValue instanceof Boolean b) {
                listTag.addByte((byte) (b ? 1 : 0));
            } else if (rawValue instanceof Long l) {
                listTag.addLong(l);
            } else if (rawValue instanceof Double d) {
                double val = d;
                if (val == Math.floor(val) && val <= Integer.MAX_VALUE && val >= Integer.MIN_VALUE) {
                    listTag.addInt((int) val);
                } else {
                    listTag.addDouble(val);
                }
            } else if (rawValue instanceof String s) {
                listTag.addString(s);
            }
        }
        return listTag;
    }
}
