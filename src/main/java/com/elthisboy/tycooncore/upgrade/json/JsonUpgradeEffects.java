package com.elthisboy.tycooncore.upgrade.json;

/**
 * Persistent bonuses applied dynamically at runtime (income, stats).
 *
 * Fields use camelCase in Java; Gson maps them from snake_case JSON keys.
 */
public class JsonUpgradeEffects {

    // ── Gym income system ─────────────────────────────────────────────────────

    /**
     * Flat income added to the player's income_per_second for EACH upgrade level.
     *
     * Example: income_bonus=10, player buys L1 → +10/s.  Buys L2 → +10/s more.
     *
     * This is the PRIMARY income field for the /tycoon gym system.
     * income_per_second = BASE(5) + Σ(incomeBonus × currentLevel)
     */
    public int incomeBonus = 0;

    // ── Legacy / PassiveIncomeScheduler fields ────────────────────────────────

    /** Flat bonus for the auto PassiveIncomeScheduler (separate from gym income). */
    public double passiveIncomeBonus = 0.0;

    /** Income multiplier for the auto scheduler. */
    public double incomeMultiplier = 0.0;

    /** Client flow bonus (drives XP grants from auto scheduler). */
    public double clientFlowBonus = 0.0;

    /** Negative-effect cleanse probability per income cycle. */
    public double negativeEffectReduction = 0.0;
}
