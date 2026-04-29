package com.elthisboy.tycooncore.fitness;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import net.minecraft.scoreboard.ScoreboardCriterion;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;

/**
 * Manages per-player fitness scores.
 *
 * ── Flow ──────────────────────────────────────────────────────────────────────
 *   1. ExerciseManager calls {@link #addPoints} every tick per exercising player.
 *   2. addPoints increments the score, updates the scoreboard objective, then
 *      checks every configured milestone in order.
 *   3. If a milestone threshold is reached and has not already been triggered
 *      (or is repeatable), its commands are executed at server level.
 *
 * ── Scoreboard ────────────────────────────────────────────────────────────────
 *   Objective name / display name are read from FitnessConfig (fitness.json).
 *   The objective is created lazily on first use so it is always present even if
 *   the server restarts with an empty scoreboard.
 */
public class FitnessManager {

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Adds {@code amount} fitness points to a player, updates the scoreboard,
     * and fires any newly-crossed milestone commands.
     */
    public static void addPoints(ServerPlayerEntity player, int amount) {
        if (amount <= 0) return;

        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);
        PlayerData data = manager.getOrCreate(player.getUuid());

        int scoreBefore = data.fitnessScore;
        data.fitnessScore += amount;
        manager.set(player.getUuid(), data);

        updateScoreboard(server, player, data.fitnessScore);
        checkMilestones(player, data, manager, server);

    }

    /**
     * Called on player join to sync their stored score to the scoreboard.
     */
    public static void syncOnJoin(ServerPlayerEntity player, PlayerData data) {
        MinecraftServer server = player.getServer();
        if (server == null) return;
        updateScoreboard(server, player, data.fitnessScore);

    }

    // ── Scoreboard ────────────────────────────────────────────────────────────

    private static void updateScoreboard(MinecraftServer server,
                                          ServerPlayerEntity player, int score) {
        ServerScoreboard scoreboard = server.getScoreboard();
        ScoreboardObjective obj = ensureObjective(scoreboard);
        if (obj == null) return;

        scoreboard.getOrCreateScore(player, obj).setScore(score);
    }

    /**
     * Returns the fitness scoreboard objective, creating it if it does not exist.
     */
    private static ScoreboardObjective ensureObjective(ServerScoreboard scoreboard) {
        FitnessConfig cfg = FitnessConfigLoader.get();
        ScoreboardObjective obj = scoreboard.getNullableObjective(cfg.scoreboardObjective);
        if (obj == null) {
            try {
                obj = scoreboard.addObjective(
                    cfg.scoreboardObjective,
                    ScoreboardCriterion.DUMMY,
                    Text.translatable("tycoon.fitness.display_name"),
                    ScoreboardCriterion.RenderType.INTEGER,
                    false,
                    null
                );
                TycoonCore.LOGGER.info("[TycoonCore] Created scoreboard objective '{}'.",
                    cfg.scoreboardObjective);
            } catch (Exception e) {
                TycoonCore.LOGGER.warn("[TycoonCore] Could not create scoreboard objective: {}",
                    e.getMessage());
            }
        }
        return obj;
    }

    // ── Milestone checking ────────────────────────────────────────────────────

    private static void checkMilestones(ServerPlayerEntity player, PlayerData data,
                                         PlayerDataManager manager, MinecraftServer server) {
        FitnessConfig cfg = FitnessConfigLoader.get();

        for (FitnessConfig.Milestone milestone : cfg.milestones) {
            if (data.fitnessScore < milestone.threshold) continue;

            boolean alreadyTriggered = data.triggeredMilestones.contains(milestone.threshold);
            if (!milestone.repeatable && alreadyTriggered) continue;

            // Mark as triggered (for non-repeatable milestones)
            if (!milestone.repeatable) {
                data.triggeredMilestones.add(milestone.threshold);
                manager.set(player.getUuid(), data);
            }

            runMilestoneCommands(player, milestone, data.fitnessScore, server);

            TycoonCore.LOGGER.info("[TycoonCore] {} reached fitness milestone {} ('{}').",
                player.getName().getString(), milestone.threshold, milestone.label);
        }
    }

    // ── Action bar HUD ────────────────────────────────────────────────────────

    /**
     * Sends a dynamic fitness action bar to the player (above the hotbar).
     * Shows current score, a 10-segment progress bar, and the next milestone label.
     * Call once per second while the player is exercising.
     */
    public static void sendActionBarUpdate(ServerPlayerEntity player) {
        MinecraftServer server = player.getServer();
        if (server == null) return;

        PlayerData data = PlayerDataManager.getOrCreate(server).getOrCreate(player.getUuid());
        int score = data.fitnessScore;
        FitnessConfig cfg = FitnessConfigLoader.get();

        // Find the highest passed threshold (prevThreshold) and the next milestone
        FitnessConfig.Milestone next = null;
        int prevThreshold = 0;
        for (FitnessConfig.Milestone m : cfg.milestones) {
            if (score >= m.threshold) {
                prevThreshold = m.threshold;
            } else if (next == null) {
                next = m;
                break;
            }
        }

        // ── Income prefix (if gym is open) ────────────────────────────────────
        MutableText bar = Text.literal("");
        if (data.gymActive) {
            String sym = GymCoreConfigLoader.get().currencySymbol;
            if (SabotageManager.isActive(player.getUuid())) {
                bar.append(Text.literal("§c⚠ §c§lBloqueado §r§8| "));
            } else if (data.incomePerSecond > 0) {
                bar.append(Text.literal("§a+" + sym
                    + GymSessionManager.formatMoney(data.incomePerSecond) + "/s §8| "));
            }
        }

        // ── Fitness section ───────────────────────────────────────────────────
        bar.append(Text.literal("§6⚡ §7"))
            .append(Text.translatable("tycoon.fitness.display_name"))
            .append(Text.literal(": §e" + score));

        if (next == null) {
            // All milestones reached
            bar.append(Text.literal(" §6§l★ MAX §r§6★"));
        } else {
            int range   = next.threshold - prevThreshold;
            int done    = score - prevThreshold;
            int percent = range > 0 ? (int)(done * 100.0 / range) : 100;
            int filled  = percent / 10;

            // 10-segment bar
            StringBuilder pbar = new StringBuilder(" §8[");
            for (int i = 0; i < 10; i++) {
                pbar.append(i < filled ? "§a█" : "§8█");
            }
            pbar.append("§8] §8→ §b")
                .append(next.label)
                .append(" §8(§7").append(percent).append("%§8)");

            bar.append(Text.literal(pbar.toString()));
        }

        player.sendMessage(bar, true); // true = action bar (above hotbar)
    }


    // ── Command execution ─────────────────────────────────────────────────────

    private static void runMilestoneCommands(ServerPlayerEntity player,
                                              FitnessConfig.Milestone milestone,
                                              int score, MinecraftServer server) {
        if (milestone.commands == null || milestone.commands.isEmpty()) return;

        var cmdManager = server.getCommandManager();
        // Run at server level (permission 4) so any command is allowed
        var src = server.getCommandSource().withLevel(4);

        for (String cmd : milestone.commands) {
            try {
                String resolved = cmd
                    .replace("%player%", player.getName().getString())
                    .replace("%score%",  String.valueOf(score))
                    .replace("%label%",  milestone.label);
                cmdManager.executeWithPrefix(src, resolved);
            } catch (Exception e) {
                TycoonCore.LOGGER.warn("[TycoonCore] Fitness command failed '{}': {}",
                    cmd, e.getMessage());
            }
        }
    }
}
