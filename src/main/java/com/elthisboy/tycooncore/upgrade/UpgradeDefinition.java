package com.elthisboy.tycooncore.upgrade;

public class UpgradeDefinition {
    public final String id;
    public final UpgradeType type;
    public final long baseCost;
    /** Cost multiplier per level: cost = baseCost * (1 + costScaling * currentLevel) */
    public final float costScaling;
    public final int requiredTier;
    public final int maxLevel;
    /** Whether purchasing this upgrade increments upgrade_progress */
    public final boolean countsAsProgress;
    public final String displayName;
    public final String description;

    public UpgradeDefinition(String id, UpgradeType type, long baseCost, float costScaling,
                              int requiredTier, int maxLevel, boolean countsAsProgress,
                              String displayName, String description) {
        this.id = id;
        this.type = type;
        this.baseCost = baseCost;
        this.costScaling = costScaling;
        this.requiredTier = requiredTier;
        this.maxLevel = maxLevel;
        this.countsAsProgress = countsAsProgress;
        this.displayName = displayName;
        this.description = description;
    }

    public long getCostForLevel(int currentLevel) {
        return (long) (baseCost * (1.0f + costScaling * currentLevel));
    }
}
