package com.elthisboy.tycooncore.network.packet;

/**
 * Compact, client-safe snapshot of one upgrade definition.
 * Sent from server → client inside {@link UpgradeMetaSyncPayload}.
 *
 * Contains everything the {@code PcScreen} needs to render the upgrade cards,
 * including the income bonus so "+X/s" can be shown without server round-trips.
 */
public record UpgradeClientEntry(
    String  id,
    String  category,
    int     maxLevel,
    int     requiredTier,
    long    baseCost,
    float   costMultiplier,
    boolean countsForProgress,
    int     incomeBonus          // flat income/s gained per upgrade level
) {
    /** Exponential cost: baseCost × costMultiplier ^ currentLevel */
    public long getCostForLevel(int currentLevel) {
        return Math.max(1L, (long) (baseCost * Math.pow(costMultiplier, currentLevel)));
    }

    /** Translation key → pc.upgrade.<id>.name */
    public String getNameKey() {
        return "pc.upgrade." + id + ".name";
    }

    /** Translation key → pc.upgrade.<id>.desc */
    public String getDescKey() {
        return "pc.upgrade." + id + ".desc";
    }
}
