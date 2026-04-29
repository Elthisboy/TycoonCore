package com.elthisboy.tycooncore.config;

import com.elthisboy.tycooncore.TycoonCore;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and saves config/gymcore/config.json.
 * Creates a default file on first run.
 * Uses snake_case JSON field names to match the spec format.
 */
public class GymCoreConfigLoader {

    private static GymCoreConfig instance = null;

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();

    public static GymCoreConfig get() {
        if (instance == null) instance = new GymCoreConfig();
        return instance;
    }

    public static void load() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                String json = Files.readString(path, StandardCharsets.UTF_8);
                instance = GSON.fromJson(json, GymCoreConfig.class);
                if (instance == null) instance = new GymCoreConfig();
                // Migrate: if tier_locations is empty, populate with defaults
                if (instance.tierLocations == null || instance.tierLocations.isEmpty()) {
                    instance.tierLocations = GymCoreConfig.buildDefaultTierLocations();
                }
                // Write back to fill in any fields that were missing from an older version
                save();
            } else {
                instance = new GymCoreConfig();
                save();
                TycoonCore.LOGGER.info("[TycoonCore] Created default config at {}", path);
            }
        } catch (IOException e) {
            TycoonCore.LOGGER.error("[TycoonCore] Failed to load config: {}", e.getMessage());
            instance = new GymCoreConfig();
        }
    }

    public static void save() {
        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, GSON.toJson(instance), StandardCharsets.UTF_8);
        } catch (IOException e) {
            TycoonCore.LOGGER.error("[TycoonCore] Failed to save config: {}", e.getMessage());
        }
    }

    private static Path configPath() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("gymcore")
            .resolve("config.json");
    }
}
