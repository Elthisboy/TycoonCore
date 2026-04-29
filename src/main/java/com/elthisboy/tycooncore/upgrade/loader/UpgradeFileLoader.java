package com.elthisboy.tycooncore.upgrade.loader;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Scans config/gymcore/upgrades/ for JSON files and loads all upgrade definitions.
 *
 * On first run, default files are copied from the mod's bundled resources
 * (assets/tycooncore/default_upgrades/) into the config directory so server
 * admins have a ready-to-edit starting point.
 */
public class UpgradeFileLoader {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
        .create();

    private static final String[] DEFAULT_FILES = {
        "visual.json", "staff.json", "passive_income.json", "stats.json"
    };

    // ── Public API ────────────────────────────────────────────────────────────

    public static List<JsonUpgradeDefinition> loadAll() {
        Path upgradesDir = upgradesDir();
        List<JsonUpgradeDefinition> all = new ArrayList<>();

        try {
            Files.createDirectories(upgradesDir);
            copyDefaultsIfNeeded(upgradesDir);

            try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(upgradesDir, "*.json")) {
                for (Path file : stream) {
                    List<JsonUpgradeDefinition> parsed = loadFile(file);
                    all.addAll(parsed);
                }
            }
        } catch (IOException e) {
            TycoonCore.LOGGER.error("[TycoonCore] Could not read upgrades directory: {}", e.getMessage());
        }

        TycoonCore.LOGGER.info("[TycoonCore] Loaded {} upgrade definition(s) from {}",
            all.size(), upgradesDir);
        return all;
    }

    // ── File loading ──────────────────────────────────────────────────────────

    private static List<JsonUpgradeDefinition> loadFile(Path path) {
        try {
            String json = Files.readString(path, StandardCharsets.UTF_8);

            // Try as JSON array first
            try {
                Type listType = new TypeToken<List<JsonUpgradeDefinition>>() {}.getType();
                List<JsonUpgradeDefinition> list = GSON.fromJson(json, listType);
                if (list != null && !list.isEmpty()) {
                    // Filter out entries missing an id
                    list.removeIf(d -> d == null || d.id.isBlank());
                    return list;
                }
            } catch (Exception ignored) {}

            // Try as single JSON object
            JsonUpgradeDefinition single = GSON.fromJson(json, JsonUpgradeDefinition.class);
            if (single != null && !single.id.isBlank()) return List.of(single);

        } catch (Exception e) {
            TycoonCore.LOGGER.error("[TycoonCore] Failed to parse {}: {}",
                path.getFileName(), e.getMessage());
        }
        return List.of();
    }

    // ── Default file seeding ──────────────────────────────────────────────────

    private static void copyDefaultsIfNeeded(Path upgradesDir) {
        for (String fileName : DEFAULT_FILES) {
            Path target = upgradesDir.resolve(fileName);

            // Always overwrite bundled defaults so changes to the mod's
            // resource files are picked up on the next server start.
            // Custom/extra JSON files placed in the upgrades dir are left untouched.
            String resourcePath = "/assets/tycooncore/default_upgrades/" + fileName;
            try (InputStream is = UpgradeFileLoader.class.getResourceAsStream(resourcePath)) {
                if (is == null) {
                    TycoonCore.LOGGER.warn("[TycoonCore] Built-in default not found: {}", resourcePath);
                    continue;
                }
                Files.copy(is, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                TycoonCore.LOGGER.info("[TycoonCore] Updated default upgrade file: {}", target.getFileName());
            } catch (IOException e) {
                TycoonCore.LOGGER.error("[TycoonCore] Could not copy default {}: {}", fileName, e.getMessage());
            }
        }
    }

    private static Path upgradesDir() {
        return FabricLoader.getInstance().getConfigDir()
            .resolve("gymcore")
            .resolve("upgrades");
    }
}
