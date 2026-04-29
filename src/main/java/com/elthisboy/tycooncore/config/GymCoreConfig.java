package com.elthisboy.tycooncore.config;

/**
 * Runtime configuration for the gym tycoon system.
 * Serialised to/from config/gymcore/config.json (snake_case via Gson policy).
 */
public class GymCoreConfig {

    /** Name of the Minecraft scoreboard objective that stores player money. */
    public String scoreboardName = "money";

    /** Symbol prepended to money values in the GUI. */
    public String currencySymbol = "$";

    /** Master toggle for the passive income ticker. */
    public boolean enablePassiveIncome = true;

    /**
     * How many server ticks between each passive income payout.
     * Default 200 = 10 seconds at 20 tps.
     */
    public int passiveIncomeIntervalTicks = 200;

    /**
     * Teleport destinations per tier (key = the NEW tier reached, e.g. 2 or 3).
     * Leave empty to disable teleport on tier-up.
     * Example:  "tier_locations": { "2": { "x": 100, "y": 64, "z": 0 },
     *                                "3": { "x": 500, "y": 64, "z": 0 } }
     */
    public java.util.Map<Integer, TierLocation> tierLocations = buildDefaultTierLocations();

    public static class TierLocation {
        public double x   = 0.5;
        public double y   = 64.0;
        public double z   = 0.5;
        public float  yaw = 0.0f;
    }

    public static java.util.Map<Integer, TierLocation> buildDefaultTierLocations() {
        java.util.Map<Integer, TierLocation> map = new java.util.LinkedHashMap<>();
        TierLocation t2 = new TierLocation(); t2.x = 226.5; t2.y = -34.0; t2.z = 118.5;
        TierLocation t3 = new TierLocation(); t3.x = 421.5; t3.y = -40.0; t3.z = 122.5;
        map.put(2, t2);
        map.put(3, t3);
        return map;
    }

    /**
     * Commands to run when a player opens or closes their gym, per tier.
     * Key = tier level (1, 2, 3, …).
     * Placeholders: %player% → player name, %tier% → current tier number.
     * Commands run at server level 4 (op-level), so any command is allowed.
     *
     * Example:
     *   "tier_commands": {
     *     "1": { "on_open": ["title %player% title \"§aTienda abierta\""], "on_close": [] },
     *     "2": { "on_open": ["playsound entity.player.levelup master %player%"], "on_close": [] }
     *   }
     */
    public java.util.Map<Integer, TierCommands> tierCommands = new java.util.LinkedHashMap<>();

    public static class TierCommands {
        /** Commands executed when the player opens their gym. */
        public java.util.List<String> onOpen  = new java.util.ArrayList<>();
        /** Commands executed when the player closes their gym. */
        public java.util.List<String> onClose = new java.util.ArrayList<>();
    }

    /**
     * How many seconds the supervisor approval countdown lasts before the tier-up fires.
     * Default: 10 seconds.
     */
    public int supervisorCountdownSeconds = 10;

    /**
     * Commands executed after the supervisor countdown completes and the tier-up happens.
     * Key = the NEW tier the player just reached (2 or 3).
     * Placeholders: %player% → gym owner name, %supervisor% → supervisor name,
     *               %tier% → new tier number.
     * Commands run at server level 4.
     *
     * Example:
     *   "supervisor_approval_commands": {
     *     "2": ["tp %player% 100 64 200", "tp %supervisor% 100 64 200"],
     *     "3": ["tp %player% 500 64 300", "tp %supervisor% 500 64 300"]
     *   }
     */
    public java.util.Map<Integer, java.util.List<String>> supervisorApprovalCommands =
        new java.util.LinkedHashMap<>();
}
