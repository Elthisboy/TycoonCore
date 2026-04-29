package com.elthisboy.tycooncore.upgrade;

import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import com.elthisboy.tycooncore.upgrade.loader.UpgradeFileLoader;

import java.util.*;

/**
 * Central in-memory registry of all upgrade definitions.
 *
 * Backed entirely by JSON files loaded from config/gymcore/upgrades/.
 * Call {@link #load()} once at startup (and optionally on /reload).
 * Everything else reads from this class – no hardcoded upgrade data anywhere.
 */
public class UpgradeRegistry {

    private static final Map<String, JsonUpgradeDefinition> UPGRADES = new LinkedHashMap<>();

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    /** Clears and reloads all upgrades from disk. */
    public static void load() {
        UPGRADES.clear();
        for (JsonUpgradeDefinition def : UpgradeFileLoader.loadAll()) {
            if (def.id == null || def.id.isBlank()) continue;
            UPGRADES.put(def.id, def);
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static JsonUpgradeDefinition get(String id) {
        return UPGRADES.get(id);
    }

    /** Returns all upgrades belonging to {@code category}, in insertion order. */
    public static List<JsonUpgradeDefinition> getByCategory(String category) {
        List<JsonUpgradeDefinition> result = new ArrayList<>();
        for (JsonUpgradeDefinition def : UPGRADES.values()) {
            if (def.category.equalsIgnoreCase(category)) result.add(def);
        }
        return result;
    }

    /** Ordered list of unique category names, preserving first-seen order. */
    public static List<String> getCategories() {
        List<String> cats = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonUpgradeDefinition def : UPGRADES.values()) {
            if (seen.add(def.category)) cats.add(def.category);
        }
        return cats;
    }

    public static Map<String, JsonUpgradeDefinition> getAll() {
        return Collections.unmodifiableMap(UPGRADES);
    }

    public static int size() {
        return UPGRADES.size();
    }
}
