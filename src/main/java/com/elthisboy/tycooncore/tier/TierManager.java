package com.elthisboy.tycooncore.tier;

import com.elthisboy.tycooncore.data.PlayerData;

public class TierManager {

    public static final int MAX_TIER = 3;

    /**
     * Number of upgrade-level purchases (with counts_for_progress=true) required
     * to advance FROM the given tier to the next.
     *
     * Progress increments by 1 on every level purchase, not just the first.
     *
     * T1 → T2 : 6 points  (e.g. buy 6 different T1 upgrades to level 1)
     * T2 → T3 : 10 points (e.g. buy all 6 T2 upgrades to Lv1 = 6, then
     *                       level up 4 of them to Lv2 = 4 more → total 10)
     *           Max possible in T2: 6 upgrades × 3 levels = 18 points.
     */
    public static int getRequiredUpgrades(int tier) {
        return switch (tier) {
            case 1 -> 6;   // buy all 6 T1 upgrades once → advance to T2
            case 2 -> 6;   // buy all 6 T2 upgrades once → advance to T3
            default -> 6;
        };
    }

    /**
     * Checks whether upgrade_progress has reached the threshold for the current tier
     * and, if so, advances tier_level and resets upgrade_progress.
     *
     * @return true if a tier-up occurred
     */
    public static boolean checkAndAdvanceTier(PlayerData data) {
        if (data.tierLevel >= MAX_TIER) return false;

        int required = getRequiredUpgrades(data.tierLevel);
        if (data.upgradeProgress >= required) {
            data.tierLevel++;
            data.upgradeProgress = 0;
            return true;
        }
        return false;
    }

    /** Income multiplier applied to all income calculations based on tier. */
    public static float getTierMultiplier(int tier) {
        return switch (tier) {
            case 1 -> 1.0f;
            case 2 -> 1.5f;
            case 3 -> 2.0f;
            default -> 1.0f + (tier - 1) * 0.5f;
        };
    }
}
