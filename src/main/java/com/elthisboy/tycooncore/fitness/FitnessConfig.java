package com.elthisboy.tycooncore.fitness;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for the Fitness Score system.
 *
 * Loaded from  config/tycooncore/fitness.json
 * Written with defaults if the file is missing.
 *
 * ── How it works ─────────────────────────────────────────────────────────────
 *   Every tick a player is using a gym machine, they earn <pointsPerTick> points.
 *   The total is displayed on the scoreboard as "Ejercitado".
 *   When a player's score crosses a milestone threshold for the first time,
 *   the configured commands are executed with server-level permission.
 *
 * ── Placeholders in commands ─────────────────────────────────────────────────
 *   %player%  → player's username
 *   %score%   → player's current fitness score
 *   %label%   → milestone label (if any)
 */
public class FitnessConfig {

    /** Points awarded per game tick (20 ticks = 1 second) while exercising. */
    public int pointsPerTick = 1;

    /** Internal scoreboard objective name (must be ≤16 chars). */
    public String scoreboardObjective = "tycoon_fitness";

    /** Display name shown on the scoreboard sidebar. */
    public String scoreboardDisplayName = "Ejercitado";

    /** List of milestones ordered by threshold. */
    public List<Milestone> milestones = new ArrayList<>();

    /**
     * Body model phases — ordered by threshold ascending.
     * When the player's score first crosses a threshold, the corresponding
     * CPM model is applied via /cpmclient set_model <model>.
     */
    public List<BodyModel> bodyModels = new ArrayList<>();

    // ─────────────────────────────────────────────────────────────────────────

    public static class Milestone {

        /** Fitness score that triggers this milestone. */
        public int threshold = 100;

        /** Short label for the milestone (used in %label% placeholder). */
        public String label = "Principiante";

        /**
         * If false (default), commands execute exactly once per player lifetime.
         * If true, commands execute every time the player reaches this threshold
         * (useful for periodic rewards).
         */
        public boolean repeatable = false;

        /**
         * Commands executed when this milestone is reached.
         * Run at server level (permission 4).
         * Placeholders: %player%, %score%, %label%
         */
        public List<String> commands = new ArrayList<>();
    }

    // ─────────────────────────────────────────────────────────────────────────

    public static class BodyModel {

        /**
         * Minimum fitness score required to enter this body phase.
         * Phase 1 should have threshold = 0 (active from the start).
         */
        public int threshold = 0;

        /** CPM model filename to apply (e.g. "skinny1.cpmmodel"). */
        public String model = "";
    }
}
