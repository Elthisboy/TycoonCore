package com.elthisboy.tycooncore.upgrade.json;

import java.util.ArrayList;
import java.util.List;

/**
 * Full definition of a single upgrade as loaded from a JSON file.
 *
 * JSON example:
 * {
 *   "id": "floor_upgrade",
 *   "category": "visual",
 *   "max_level": 5,
 *   "base_cost": 200,
 *   "cost_multiplier": 1.5,
 *   "required_tier": 1,
 *   "counts_for_progress": true,
 *   "actions": [ { "type": "command", "value": "say Upgrading floor..." } ],
 *   "effects": { "income_multiplier": 0.05 }
 * }
 *
 * Gson maps camelCase → snake_case via FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES.
 */
public class JsonUpgradeDefinition {

    public String id = "";
    public String category = "visual";
    public int maxLevel = 5;
    public long baseCost = 100;
    public float costMultiplier = 1.5f;
    public int requiredTier = 1;
    public boolean countsForProgress = true;
    public List<JsonUpgradeAction> actions = new ArrayList<>();
    public JsonUpgradeEffects effects = new JsonUpgradeEffects();

    // ── Translation keys derived from id ─────────────────────────────────────

    /** e.g. "pc.upgrade.floor_upgrade.name" */
    public String getNameKey() {
        return "pc.upgrade." + id + ".name";
    }

    /** e.g. "pc.upgrade.floor_upgrade.desc" */
    public String getDescKey() {
        return "pc.upgrade." + id + ".desc";
    }

    /**
     * Exponential cost scaling.
     * cost(level) = baseCost * costMultiplier^currentLevel
     */
    public long getCostForLevel(int currentLevel) {
        return Math.max(1L, (long) (baseCost * Math.pow(costMultiplier, currentLevel)));
    }
}
