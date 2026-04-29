package com.elthisboy.tycooncore.client;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Client-side cache for the most recent {@code PlayerDataSyncPayload}.
 *
 * Updated by {@code ClientNetworkHandler} on every sync packet.
 * {@code PcScreen} reads these fields every render frame — always current.
 */
public final class ClientPlayerDataCache {

    public static volatile int     tierLevel       = 1;
    public static volatile int     upgradeProgress = 0;
    public static volatile long    money           = 0L;

    /** Cached income per second (sum of all upgrade bonuses + 5 base). */
    public static volatile int     incomePerSecond = 5;

    /** Whether the gym is currently open (set by /tycoon gym start|end). */
    public static volatile boolean gymActive       = false;

    // Not volatile — replaced atomically as a whole reference
    private static volatile Map<String, Integer> upgradeLevels = new HashMap<>();

    private ClientPlayerDataCache() {}

    // ── Update ────────────────────────────────────────────────────────────────

    public static void update(int tier, int progress, long money,
                               Map<String, Integer> levels,
                               int incomePerSecond, boolean gymActive) {
        tierLevel                        = tier;
        upgradeProgress                  = progress;
        ClientPlayerDataCache.money      = money;
        upgradeLevels                    = new HashMap<>(levels);
        ClientPlayerDataCache.incomePerSecond = incomePerSecond;
        ClientPlayerDataCache.gymActive  = gymActive;
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    public static int getUpgradeLevel(String id) {
        return upgradeLevels.getOrDefault(id, 0);
    }

    public static Map<String, Integer> getUpgradeLevels() {
        return Collections.unmodifiableMap(upgradeLevels);
    }
}
