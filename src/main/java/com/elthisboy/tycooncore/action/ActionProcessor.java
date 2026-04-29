package com.elthisboy.tycooncore.action;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.upgrade.UpgradeRegistry;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeAction;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import net.minecraft.entity.attribute.EntityAttribute;
import net.minecraft.entity.attribute.EntityAttributeInstance;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.network.packet.s2c.play.ClearTitleS2CPacket;
import net.minecraft.network.packet.s2c.play.SubtitleS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleFadeS2CPacket;
import net.minecraft.network.packet.s2c.play.TitleS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;
import java.util.Map;

/**
 * Processes the action list of an upgrade sequentially.
 *
 * Supported action types
 * ─────────────────────────────────────────────────────────────────────────────
 *   "command"
 *       Runs a server command as OP level 4.
 *       Placeholders: {player}, {level}, {uuid}, {x}, {y}, {z}
 *
 *   "schematic"
 *       Triggers schematic placement via a configurable command template.
 *       "file"  or "value" → schematic identifier (no extension)
 *       "params.command"   → command template (WorldEdit / FAWE / StructScript)
 *       Placeholders: {schematic}, {player}, {level}, {x}, {y}, {z}
 *
 *   "effect"
 *       Built-in named modifier (legacy / shorthand).
 *       "value" → "speed" | … (kept for backwards-compat)
 *
 *   "potion"
 *       Applies a vanilla status effect for a very long duration.
 *       "value"                     → vanilla effect id  (e.g. "strength")
 *       params.amplifier_per_level  → int, default 0 (level 1 = amplifier 0 = Strength I)
 *       params.duration_ticks       → int, default 9999999 (~138 h, effectively permanent)
 *       params.show_particles       → bool, default "true"
 *       Effect is re-applied on login via reapplyPersistentEffects().
 *
 *   "attribute"
 *       Applies a persistent EntityAttributeModifier.
 *       "value"                  → vanilla attribute path, e.g. "generic.movement_speed"
 *       params.value_per_level   → double per level added to the modifier value
 *       params.operation         → add_value | add_multiplied_base | add_multiplied_total
 *       params.modifier_id       → optional unique key (defaults to attribute path)
 *       Modifier is keyed by Identifier so purchasing again replaces the old one.
 *       Re-applied on login via reapplyPersistentEffects().
 *
 *   "title"
 *       Sends a title screen message to the buying player.
 *       "value"           → title text (supports placeholders)
 *       params.subtitle   → optional subtitle text
 *       params.fade_in    → int ticks, default 10
 *       params.stay       → int ticks, default 60
 *       params.fade_out   → int ticks, default 20
 *
 *   "broadcast"
 *       Broadcasts a chat message to every online player.
 *       "value" → message text (supports {player}, {level})
 */
public class ActionProcessor {

    // ── Entry points ──────────────────────────────────────────────────────────

    /** Execute all actions associated with an upgrade purchase. */
    public static void process(ServerPlayerEntity player,
                               List<JsonUpgradeAction> actions,
                               int newLevel) {
        for (JsonUpgradeAction action : actions) {
            if (action == null || action.type == null) continue;
            try {
                switch (action.type.toLowerCase().trim()) {
                    case "command"   -> handleCommand(player, action, newLevel);
                    case "schematic" -> handleSchematic(player, action, newLevel);
                    case "effect"    -> handleEffect(player, action, newLevel);
                    case "potion"    -> handlePotion(player, action, newLevel);
                    case "attribute" -> handleAttribute(player, action, newLevel);
                    case "title"     -> handleTitle(player, action, newLevel);
                    case "broadcast" -> handleBroadcast(player, action, newLevel);
                    default -> TycoonCore.LOGGER.warn(
                        "[TycoonCore] Unknown action type '{}' – skipping", action.type);
                }
            } catch (Exception e) {
                TycoonCore.LOGGER.error("[TycoonCore] Error in action '{}': {}",
                    action.type, e.getMessage());
            }
        }
    }

    /**
     * Re-applies all persistent effect/potion/attribute actions for every upgrade
     * the player currently owns.  Call this on player JOIN so stat boosts survive
     * server restarts, deaths, and re-logins.
     */
    public static void reapplyPersistentEffects(ServerPlayerEntity player, PlayerData data) {
        for (Map.Entry<String, Integer> entry : data.upgradeLevels.entrySet()) {
            int level = entry.getValue();
            if (level <= 0) continue;
            JsonUpgradeDefinition def = UpgradeRegistry.get(entry.getKey());
            if (def == null || def.actions == null) continue;
            for (JsonUpgradeAction action : def.actions) {
                if (action == null || action.type == null) continue;
                String t = action.type.toLowerCase().trim();
                if (!t.equals("effect") && !t.equals("potion") && !t.equals("attribute")) continue;
                try {
                    switch (t) {
                        case "effect"    -> handleEffect(player, action, level);
                        case "potion"    -> handlePotion(player, action, level);
                        case "attribute" -> handleAttribute(player, action, level);
                    }
                } catch (Exception e) {
                    TycoonCore.LOGGER.warn("[TycoonCore] reapply '{}' for {}: {}",
                        action.type, player.getName().getString(), e.getMessage());
                }
            }
        }
    }

    // ── Command ───────────────────────────────────────────────────────────────

    private static void handleCommand(ServerPlayerEntity player,
                                      JsonUpgradeAction action,
                                      int newLevel) {
        // Optional "only_level" param — skip if the purchased level doesn't match
        String onlyLevel = action.params.getOrDefault("only_level", null);
        if (onlyLevel != null && !onlyLevel.trim().equals(String.valueOf(newLevel))) return;

        String cmd = replacePlaceholders(action.value, player, newLevel, null);
        runCommand(player.getServer(), cmd);
    }

    // ── Schematic ─────────────────────────────────────────────────────────────

    private static void handleSchematic(ServerPlayerEntity player,
                                        JsonUpgradeAction action,
                                        int newLevel) {
        String schematicName = (action.file != null && !action.file.isBlank())
            ? action.file : action.value;
        schematicName = replacePlaceholders(schematicName, player, newLevel, null);

        String cmdTemplate = action.params.getOrDefault(
            "command",
            "say §a[Structure] Placing §e" + schematicName + " §afor §e{player}"
        );
        String cmd = replacePlaceholders(cmdTemplate, player, newLevel, schematicName);
        runCommand(player.getServer(), cmd);
    }

    // ── Effect (legacy built-in) ──────────────────────────────────────────────

    private static void handleEffect(ServerPlayerEntity player,
                                     JsonUpgradeAction action,
                                     int newLevel) {
        String effectId = action.value == null ? "" : action.value.toLowerCase().trim();
        switch (effectId) {
            case "speed" -> applyAttributeModifier(
                player, "movement_speed",
                Identifier.of("tycooncore", "speed_upgrade"),
                newLevel * 0.10,
                EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
            default -> TycoonCore.LOGGER.warn(
                "[TycoonCore] Unknown effect id '{}' – use 'potion' or 'attribute' instead",
                action.value);
        }
    }

    // ── Potion ────────────────────────────────────────────────────────────────

    /**
     * Applies a vanilla status effect for a very long duration (effectively permanent
     * until death). Replaces any existing instance of the same effect.
     *
     * JSON example:
     * { "type": "potion", "value": "strength",
     *   "params": { "amplifier_per_level": "1", "duration_ticks": "9999999" } }
     */
    private static void handlePotion(ServerPlayerEntity player,
                                     JsonUpgradeAction action,
                                     int newLevel) {
        if (action.value == null || action.value.isBlank()) return;
        String effectId = action.value.toLowerCase().trim();

        int ampPerLevel  = parseIntParam(action, "amplifier_per_level", 0);
        int duration     = parseIntParam(action, "duration_ticks",      9_999_999);
        boolean showPart = !"false".equalsIgnoreCase(
            action.params.getOrDefault("show_particles", "true"));

        // amplifier: level 1 with amp 1 = Strength I (amplifier 0), level 2 = Strength II etc.
        int amplifier = Math.max(0, newLevel * ampPerLevel - 1);

        RegistryEntry<StatusEffect> effectEntry =
            Registries.STATUS_EFFECT.getEntry(Identifier.ofVanilla(effectId)).orElse(null);
        if (effectEntry == null) {
            TycoonCore.LOGGER.warn("[TycoonCore] Unknown potion effect id '{}'", effectId);
            return;
        }

        player.addStatusEffect(new StatusEffectInstance(
            effectEntry, duration, amplifier, false, showPart, true));
    }

    // ── Attribute ─────────────────────────────────────────────────────────────

    /**
     * Applies a persistent EntityAttributeModifier.  Purchasing the same upgrade
     * multiple times replaces the modifier (idempotent via stable Identifier key).
     *
     * JSON example:
     * { "type": "attribute", "value": "generic.movement_speed",
     *   "params": { "value_per_level": "0.03",
     *               "operation": "add_value",
     *               "modifier_id": "manager_speed" } }
     */
    private static void handleAttribute(ServerPlayerEntity player,
                                        JsonUpgradeAction action,
                                        int newLevel) {
        if (action.value == null || action.value.isBlank()) return;
        String attrPath   = action.value.toLowerCase().trim();
        double perLevel   = parseDoubleParam(action, "value_per_level", 0.0);
        String opStr      = action.params.getOrDefault("operation", "add_value");
        String modKey     = action.params.getOrDefault("modifier_id",
            attrPath.replace(".", "_") + "_tycoon");
        Identifier modId  = Identifier.of("tycooncore", modKey);

        EntityAttributeModifier.Operation op = switch (opStr.toLowerCase()) {
            case "add_multiplied_base"  -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_BASE;
            case "add_multiplied_total" -> EntityAttributeModifier.Operation.ADD_MULTIPLIED_TOTAL;
            default                     -> EntityAttributeModifier.Operation.ADD_VALUE;
        };

        applyAttributeModifier(player, attrPath, modId, perLevel * newLevel, op);
    }

    // ── Title ─────────────────────────────────────────────────────────────────

    /**
     * Sends a title + optional subtitle to the buying player.
     *
     * JSON example:
     * { "type": "title", "value": "§6Upgrade Purchased!",
     *   "params": { "subtitle": "§eLevel {level} unlocked", "stay": "80" } }
     */
    private static void handleTitle(ServerPlayerEntity player,
                                    JsonUpgradeAction action,
                                    int newLevel) {
        int fadeIn  = parseIntParam(action, "fade_in",  10);
        int stay    = parseIntParam(action, "stay",     60);
        int fadeOut = parseIntParam(action, "fade_out", 20);

        String titleStr = replacePlaceholders(action.value, player, newLevel, null);
        String subStr   = replacePlaceholders(
            action.params.getOrDefault("subtitle", ""), player, newLevel, null);

        player.networkHandler.sendPacket(new ClearTitleS2CPacket(false));
        player.networkHandler.sendPacket(new TitleFadeS2CPacket(fadeIn, stay, fadeOut));
        if (!subStr.isBlank()) {
            player.networkHandler.sendPacket(new SubtitleS2CPacket(Text.literal(subStr)));
        }
        player.networkHandler.sendPacket(new TitleS2CPacket(Text.literal(titleStr)));
    }

    // ── Broadcast ─────────────────────────────────────────────────────────────

    /**
     * Sends a chat message to every online player.
     *
     * JSON example:
     * { "type": "broadcast", "value": "§a{player} §7upgraded their gym!" }
     */
    private static void handleBroadcast(ServerPlayerEntity player,
                                        JsonUpgradeAction action,
                                        int newLevel) {
        if (action.value == null || action.value.isBlank()) return;
        String msg = replacePlaceholders(action.value, player, newLevel, null);
        Text   txt = Text.literal(msg);
        if (player.getServer() == null) return;
        for (ServerPlayerEntity p : player.getServer().getPlayerManager().getPlayerList()) {
            p.sendMessage(txt, false);
        }
    }

    // ── Attribute helper ──────────────────────────────────────────────────────

    private static void applyAttributeModifier(ServerPlayerEntity player,
                                               String vanillaAttrPath,
                                               Identifier modId,
                                               double value,
                                               EntityAttributeModifier.Operation op) {
        RegistryEntry<EntityAttribute> entry =
            Registries.ATTRIBUTE.getEntry(Identifier.ofVanilla(vanillaAttrPath)).orElse(null);
        if (entry == null) {
            TycoonCore.LOGGER.warn("[TycoonCore] Unknown attribute '{}'", vanillaAttrPath);
            return;
        }
        EntityAttributeInstance attr = player.getAttributeInstance(entry);
        if (attr == null) return;
        attr.removeModifier(modId);
        if (value != 0) {
            attr.addPersistentModifier(new EntityAttributeModifier(modId, value, op));
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void runCommand(MinecraftServer server, String cmd) {
        if (server == null || cmd == null || cmd.isBlank()) return;
        ServerCommandSource source = server.getCommandSource().withLevel(4).withSilent();
        server.getCommandManager().executeWithPrefix(source, cmd);
    }

    private static String replacePlaceholders(String template,
                                              ServerPlayerEntity player,
                                              int level,
                                              String schematic) {
        if (template == null) return "";
        return template
            .replace("{player}",    player.getName().getString())
            .replace("{uuid}",      player.getUuidAsString())
            .replace("{level}",     String.valueOf(level))
            .replace("{x}",         String.valueOf(player.getBlockX()))
            .replace("{y}",         String.valueOf(player.getBlockY()))
            .replace("{z}",         String.valueOf(player.getBlockZ()))
            .replace("{schematic}", schematic != null ? schematic : "");
    }

    private static int parseIntParam(JsonUpgradeAction action, String key, int def) {
        try { return Integer.parseInt(action.params.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }

    private static double parseDoubleParam(JsonUpgradeAction action, String key, double def) {
        try { return Double.parseDouble(action.params.getOrDefault(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
}
