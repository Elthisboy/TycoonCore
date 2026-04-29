package com.elthisboy.tycooncore.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Per-player persistent data.
 *
 * Money lives on the Minecraft scoreboard (see ScoreboardEconomy).
 * Upgrade levels, tier, gym state, and cached income rate are stored here.
 */
public class PlayerData {

    public int tierLevel       = 1;
    public int upgradeProgress = 0;

    /**
     * Cached sum of all upgrade income bonuses + base income (5).
     * Recalculated by {@link com.elthisboy.tycooncore.gym.GymSessionManager#recalculateIncome}
     * after every upgrade purchase and on /tycoon gym start.
     * Stored here so the tick loop reads a single field instead of iterating upgrades.
     */
    public int incomePerSecond = 5;

    /**
     * Whether the player's gym is currently open.
     * Persisted so the gym state survives server restarts.
     * Controlled exclusively by /tycoon gym start | end.
     */
    public boolean gymActive = false;

    /** upgradeId → current level (0 = not purchased) */
    public Map<String, Integer> upgradeLevels = new HashMap<>();

    /** Cumulative fitness points earned by exercising. */
    public int fitnessScore = 0;

    /** Thresholds of milestones already fired (non-repeatable). */
    public List<Integer> triggeredMilestones = new ArrayList<>();

    /**
     * True when the player has bought all required upgrades for their current tier
     * and is waiting for a supervisor to approve the tier-up.
     * Set by UpgradeManager when progress reaches the threshold.
     * Cleared by SupervisorManager after the tier is actually advanced.
     */
    public boolean pendingTierApproval = false;

    // ── Convenience helpers ───────────────────────────────────────────────────

    public int getUpgradeLevel(String upgradeId) {
        return upgradeLevels.getOrDefault(upgradeId, 0);
    }

    public void setUpgradeLevel(String upgradeId, int level) {
        upgradeLevels.put(upgradeId, level);
    }

    // ── NBT serialisation ─────────────────────────────────────────────────────

    public NbtCompound toNbt() {
        NbtCompound nbt = new NbtCompound();
        nbt.putInt("tier_level",        tierLevel);
        nbt.putInt("upgrade_progress",  upgradeProgress);
        nbt.putInt("income_per_second", incomePerSecond);
        nbt.putBoolean("gym_active",    gymActive);

        NbtCompound levels = new NbtCompound();
        for (Map.Entry<String, Integer> e : upgradeLevels.entrySet()) {
            levels.putInt(e.getKey(), e.getValue());
        }
        nbt.put("upgrade_levels", levels);

        nbt.putInt("fitness_score", fitnessScore);
        nbt.putIntArray("triggered_milestones",
            triggeredMilestones.stream().mapToInt(Integer::intValue).toArray());
        nbt.putBoolean("pending_tier_approval", pendingTierApproval);

        return nbt;
    }

    public static PlayerData fromNbt(NbtCompound nbt) {
        PlayerData data = new PlayerData();
        data.tierLevel       = Math.max(1, nbt.getInt("tier_level"));
        data.upgradeProgress = nbt.getInt("upgrade_progress");
        data.incomePerSecond = nbt.contains("income_per_second")
                               ? Math.max(5, nbt.getInt("income_per_second")) : 5;
        data.gymActive       = nbt.contains("gym_active") && nbt.getBoolean("gym_active");

        if (nbt.contains("upgrade_levels", NbtElement.COMPOUND_TYPE)) {
            NbtCompound levels = nbt.getCompound("upgrade_levels");
            for (String key : levels.getKeys()) {
                data.upgradeLevels.put(key, levels.getInt(key));
            }
        }

        data.fitnessScore = nbt.contains("fitness_score") ? nbt.getInt("fitness_score") : 0;
        if (nbt.contains("triggered_milestones", NbtElement.INT_ARRAY_TYPE)) {
            for (int v : nbt.getIntArray("triggered_milestones")) {
                data.triggeredMilestones.add(v);
            }
        }
        data.pendingTierApproval = nbt.contains("pending_tier_approval")
            && nbt.getBoolean("pending_tier_approval");

        return data;
    }
}
