package com.elthisboy.tycooncore.equipment;

import java.util.ArrayList;
import java.util.List;

/**
 * Per-machine JSON configuration.
 *
 * Loaded from  config/tycooncore/gym_equipment/<type>.json
 * Written with defaults if the file is missing.
 *
 * All numeric timing is in TICKS (20 ticks = 1 second).
 */
public class ExerciseConfig {

    // ── Core ──────────────────────────────────────────────────────────────────

    /** Multiplier applied to the animation loop duration. 1.0 = normal speed. */
    public double animationSpeed = 1.0;

    /**
     * Base loop duration in ticks (before animationSpeed scaling).
     * One full animation cycle completes in loopDuration / animationSpeed ticks.
     */
    public int loopDuration = 20;

    /** If true, player position is locked while exercising. */
    public boolean lockPlayer = true;

    // ── Sound ─────────────────────────────────────────────────────────────────

    public SoundConfig sounds = new SoundConfig();

    public static class SoundConfig {
        public boolean enabled      = true;
        /** Vanilla or modded sound event ID, e.g. "minecraft:block.anvil.land". */
        public String  soundId      = "minecraft:block.anvil.land";
        public float   volume       = 0.35f;
        public float   pitch        = 1.2f;
        /** Play the sound every N ticks (relative to the animation loop). */
        public int     intervalTicks = 20;
    }

    // ── Commands ──────────────────────────────────────────────────────────────

    public CommandConfig commands = new CommandConfig();

    public static class CommandConfig {
        /** Set to true to actually run the commands below. */
        public boolean      enabled  = false;
        /**
         * "player" = run as the exercising player (needs permission level 0).
         * "server" = run as the server (level 4, use for force-running ops).
         */
        public String       source   = "player";
        /** Run once when the player starts exercising. */
        public List<String> onStart  = new ArrayList<>();
        /**
         * Run every second (20 ticks) while exercising.
         * Keep this list empty or very lightweight to avoid lag.
         */
        public List<String> onTick   = new ArrayList<>();
        /** Run once when the player stops exercising. */
        public List<String> onStop   = new ArrayList<>();
    }

    // ── Visual ────────────────────────────────────────────────────────────────

    public VisualConfig visualEffects = new VisualConfig();

    public static class VisualConfig {
        /** Spawn particles near the player while exercising. */
        public boolean particles    = false;
        /** Vanilla particle ID, e.g. "minecraft:happy_villager". */
        public String  particleType = "minecraft:sweat_droplets";
        /** Highlight the block outline while in use. */
        public boolean highlight    = false;
    }
}
