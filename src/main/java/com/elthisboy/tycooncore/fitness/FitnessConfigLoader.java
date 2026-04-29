package com.elthisboy.tycooncore.fitness;

import com.elthisboy.tycooncore.TycoonCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads and caches {@link FitnessConfig} from:
 *   <config_dir>/tycooncore/fitness.json
 *
 * Writes a default file if absent so admins can discover and edit it.
 * Call {@link #reload()} to hot-reload without restarting the server.
 */
public class FitnessConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FitnessConfig CACHE = null;

    // ── Public API ────────────────────────────────────────────────────────────

    public static FitnessConfig get() {
        if (CACHE == null) reload();
        return CACHE;
    }

    public static void reload() {
        Path path = configPath();
        if (!Files.exists(path)) writeDefault(path);

        try (Reader r = Files.newBufferedReader(path)) {
            FitnessConfig cfg = GSON.fromJson(r, FitnessConfig.class);
            CACHE = (cfg != null) ? cfg : defaultConfig();
            TycoonCore.LOGGER.info("[TycoonCore] Fitness config loaded ({} milestones).",
                CACHE.milestones.size());
        } catch (IOException e) {
            TycoonCore.LOGGER.error("[TycoonCore] Failed to read fitness.json: {}", e.getMessage());
            CACHE = defaultConfig();
        }
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("tycooncore")
            .resolve("fitness.json");
    }

    private static void writeDefault(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(defaultConfig(), w);
            }
            TycoonCore.LOGGER.info("[TycoonCore] Wrote default fitness config: {}", path);
        } catch (IOException e) {
            TycoonCore.LOGGER.warn("[TycoonCore] Could not write fitness.json: {}", e.getMessage());
        }
    }

    private static FitnessConfig defaultConfig() {
        FitnessConfig cfg = new FitnessConfig();
        cfg.pointsPerTick           = 1;
        cfg.scoreboardObjective     = "tycoon_fitness";
        cfg.scoreboardDisplayName   = "Ejercitado";

        cfg.milestones = new ArrayList<>(List.of(
            milestone(200,   "Recién Empezando",  false,
                "say §e%player% §7ha conseguido el rango: §b%label%"),

            milestone(1000,  "En Forma",          false,
                "say §e%player% §7ha conseguido el rango: §a%label%"),

            milestone(5000,  "Atleta",            false,
                "say §e%player% §7ha conseguido el rango: §6%label%",
                "give %player% minecraft:golden_apple 1"),

            milestone(20000, "Élite",             false,
                "say §e%player% §7ha conseguido el rango: §c%label%",
                "give %player% minecraft:diamond 3")
        ));

        // ── 5 body model phases (aligned with milestone thresholds) ───────────
        cfg.bodyModels = new ArrayList<>(List.of(
            bodyModel(0,     "skinny1.cpmmodel"),   // fase 1 — desde el inicio
            bodyModel(200,   "skinny2.cpmmodel"),   // fase 2 — Recién Empezando
            bodyModel(1000,  "fit.cpmmodel"),        // fase 3 — En Forma
            bodyModel(5000,  "fi2.cpmmodel"),        // fase 4 — Atleta
            bodyModel(20000, "ultrafit.cpmmodel")   // fase 5 — Élite
        ));

        return cfg;
    }

    private static FitnessConfig.Milestone milestone(int threshold, String label,
                                                      boolean repeatable,
                                                      String... commands) {
        FitnessConfig.Milestone m = new FitnessConfig.Milestone();
        m.threshold   = threshold;
        m.label       = label;
        m.repeatable  = repeatable;
        m.commands    = new ArrayList<>(List.of(commands));
        return m;
    }

    private static FitnessConfig.BodyModel bodyModel(int threshold, String model) {
        FitnessConfig.BodyModel bm = new FitnessConfig.BodyModel();
        bm.threshold = threshold;
        bm.model     = model;
        return bm;
    }
}
