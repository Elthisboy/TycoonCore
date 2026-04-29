package com.elthisboy.tycooncore.economy;

import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import net.minecraft.scoreboard.ScoreAccess;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.scoreboard.ServerScoreboard;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * All money operations go through here.
 *
 * The mod NEVER creates the scoreboard objective – it only reads/writes scores
 * on the objective whose name is set in config.json (scoreboardName).
 * If the objective does not exist the operation is a no-op and returns false.
 *
 * Scoreboard scores are integers; money is stored as int internally.
 * The public API uses long for future-proofing (clamped to Integer range).
 */
public class ScoreboardEconomy {

    // ── Read ─────────────────────────────────────────────────────────────────

    public static long getMoney(ServerPlayerEntity player) {
        ScoreAccess acc = access(player);
        return acc != null ? acc.getScore() : 0L;
    }

    public static boolean hasMoney(ServerPlayerEntity player, long amount) {
        return getMoney(player) >= amount;
    }

    // ── Write ─────────────────────────────────────────────────────────────────

    public static boolean setMoney(ServerPlayerEntity player, long amount) {
        ScoreAccess acc = access(player);
        if (acc == null) return false;
        acc.setScore((int) Math.min(amount, Integer.MAX_VALUE));
        return true;
    }

    public static boolean addMoney(ServerPlayerEntity player, long amount) {
        ScoreAccess acc = access(player);
        if (acc == null) return false;
        long current = acc.getScore();
        acc.setScore((int) Math.min(current + amount, Integer.MAX_VALUE));
        return true;
    }

    /**
     * Deducts {@code amount} from the player's score.
     * Returns false (and makes no change) if the objective doesn't exist
     * or the player doesn't have enough money.
     */
    public static boolean deductMoney(ServerPlayerEntity player, long amount) {
        ScoreAccess acc = access(player);
        if (acc == null) return false;
        int current = acc.getScore();
        if (current < amount) return false;
        acc.setScore((int) (current - amount));
        return true;
    }

    // ── Existence check ───────────────────────────────────────────────────────

    /** Returns true if the configured scoreboard objective exists on the server. */
    public static boolean objectiveExists(ServerPlayerEntity player) {
        if (player.getServer() == null) return false;
        return objective(player) != null;
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static ScoreAccess access(ServerPlayerEntity player) {
        if (player.getServer() == null) return null;
        ScoreboardObjective obj = objective(player);
        if (obj == null) return null;
        ServerScoreboard sb = player.getServer().getScoreboard();
        return sb.getOrCreateScore(player, obj);
    }

    private static ScoreboardObjective objective(ServerPlayerEntity player) {
        ServerScoreboard sb = player.getServer().getScoreboard();
        return sb.getNullableObjective(GymCoreConfigLoader.get().scoreboardName);
    }
}
