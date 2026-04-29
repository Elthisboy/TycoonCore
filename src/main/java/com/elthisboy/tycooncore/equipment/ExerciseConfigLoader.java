package com.elthisboy.tycooncore.equipment;

import com.elthisboy.tycooncore.TycoonCore;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

/**
 * Loads and caches per-machine {@link ExerciseConfig} objects from:
 *   <config_dir>/tycooncore/gym_equipment/<type>.json
 *
 * If a file is absent, a default config is written to disk so server admins
 * can find and edit it.
 *
 * Call {@link #reload()} at any time to hot-reload all configs without restarting.
 */
public class ExerciseConfigLoader {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final Map<EquipmentType, ExerciseConfig> CACHE =
        new EnumMap<>(EquipmentType.class);

    // ── Public API ────────────────────────────────────────────────────────────

    /** Returns the config for the given type, loading it if necessary. */
    public static ExerciseConfig get(EquipmentType type) {
        return CACHE.computeIfAbsent(type, ExerciseConfigLoader::load);
    }

    /** Reloads ALL machine configs from disk. Safe to call at runtime. */
    public static void reload() {
        CACHE.clear();
        for (EquipmentType t : EquipmentType.values()) {
            CACHE.put(t, load(t));
        }
        TycoonCore.LOGGER.info("[TycoonCore] Gym equipment configs reloaded.");
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static ExerciseConfig load(EquipmentType type) {
        Path configPath = configDir().resolve(type.configId + ".json");

        // Write default if missing
        if (!Files.exists(configPath)) {
            writeDefault(type, configPath);
        }

        try (Reader reader = Files.newBufferedReader(configPath)) {
            ExerciseConfig cfg = GSON.fromJson(reader, ExerciseConfig.class);
            if (cfg == null) cfg = defaultFor(type);
            TycoonCore.LOGGER.debug("[TycoonCore] Loaded equipment config: {}", configPath);
            return cfg;
        } catch (IOException e) {
            TycoonCore.LOGGER.error("[TycoonCore] Failed to read {}: {}", configPath, e.getMessage());
            return defaultFor(type);
        }
    }

    private static void writeDefault(EquipmentType type, Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(defaultFor(type), w);
            }
            TycoonCore.LOGGER.info("[TycoonCore] Wrote default equipment config: {}", path);
        } catch (IOException e) {
            TycoonCore.LOGGER.warn("[TycoonCore] Could not write default config {}: {}", path, e.getMessage());
        }
    }

    private static Path configDir() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("tycooncore")
            .resolve("gym_equipment");
    }

    // ── Machine-specific defaults ─────────────────────────────────────────────

    private static ExerciseConfig defaultFor(EquipmentType type) {
        ExerciseConfig cfg = new ExerciseConfig();
        switch (type) {
            case DUMBBELL_BENCH -> {
                cfg.loopDuration              = 20;    // 1 s loop — alternating curls
                cfg.animationSpeed            = 1.0;
                cfg.sounds.soundId            = "minecraft:block.anvil.land";
                cfg.sounds.volume             = 0.25f;
                cfg.sounds.pitch              = 1.6f;
                cfg.sounds.intervalTicks      = 10;
            }
            case TREADMILL -> {
                cfg.loopDuration              = 10;    // 0.5 s loop — fast running cadence
                cfg.animationSpeed            = 1.0;
                cfg.sounds.soundId            = "minecraft:block.gravel.step";
                cfg.sounds.volume             = 0.5f;
                cfg.sounds.pitch              = 1.1f;
                cfg.sounds.intervalTicks      = 5;
            }
            case BENCH_PRESS -> {
                cfg.loopDuration              = 40;    // 2 s loop — slow heavy press
                cfg.animationSpeed            = 1.0;
                cfg.sounds.soundId            = "minecraft:block.iron_door.open";
                cfg.sounds.volume             = 0.35f;
                cfg.sounds.pitch              = 0.8f;
                cfg.sounds.intervalTicks      = 40;
            }
            case CABLE_MACHINE -> {
                cfg.loopDuration              = 30;    // 1.5 s loop — cable tension
                cfg.animationSpeed            = 1.0;
                cfg.sounds.soundId            = "minecraft:block.piston.extend";
                cfg.sounds.volume             = 0.3f;
                cfg.sounds.pitch              = 1.3f;
                cfg.sounds.intervalTicks      = 15;
            }
            case DECO -> {
                cfg.loopDuration              = 20;    // 1 s loop — gentle alternating swing
                cfg.animationSpeed            = 1.0;
                cfg.lockPlayer                = false; // deco blocks don't lock position
                cfg.sounds.enabled            = false; // no sound by default
            }
            case WEIGHT_LIFT -> {
                cfg.loopDuration              = 30;    // 1.5 s loop — slow heavy lift
                cfg.animationSpeed            = 1.0;
                cfg.lockPlayer                = false; // player can walk while lifting
                cfg.sounds.soundId            = "minecraft:block.anvil.land";
                cfg.sounds.volume             = 0.2f;
                cfg.sounds.pitch              = 1.8f;  // high pitch = weight clank
                cfg.sounds.intervalTicks      = 30;    // once per rep
            }
            case TREADMILL_WALK -> {
                cfg.loopDuration              = 10;    // 0.5 s loop — fast running cadence
                cfg.animationSpeed            = 1.0;
                cfg.lockPlayer                = true;  // locked in place — running animation only
                cfg.sounds.soundId            = "minecraft:block.gravel.step";
                cfg.sounds.volume             = 0.4f;
                cfg.sounds.pitch              = 1.2f;
                cfg.sounds.intervalTicks      = 5;
            }
            case BENCH_SIT -> {
                cfg.loopDuration              = 40;    // 2 s loop — seated exercise
                cfg.animationSpeed            = 1.0;
                cfg.lockPlayer                = false; // position managed by vehicle riding
                cfg.sounds.soundId            = "minecraft:block.iron_door.open";
                cfg.sounds.volume             = 0.2f;
                cfg.sounds.pitch              = 0.9f;
                cfg.sounds.intervalTicks      = 40;
            }
        }
        return cfg;
    }
}
