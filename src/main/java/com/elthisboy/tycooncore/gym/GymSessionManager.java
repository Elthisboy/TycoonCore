package com.elthisboy.tycooncore.gym;

import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.equipment.ExerciseManager;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.elthisboy.tycooncore.upgrade.UpgradeRegistry;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Core engine of the /tycoon gym income system.
 *
 * Design principles:
 *   • Gym state (open/closed) is stored in PlayerData → persists across restarts.
 *   • income_per_second is a cached int in PlayerData, recalculated whenever
 *     an upgrade is purchased or the gym is opened.
 *   • The tick loop is intentionally simple: iterate online players, check
 *     data.gymActive, add data.incomePerSecond to the scoreboard once per second.
 *   • No in-memory state sets needed — truth lives in PlayerData.
 *
 * Income formula:
 *   income_per_second = BASE(5) + Σ( upgrade.incomeBonus × currentLevel )
 *
 * Tier 1 all bought (L1 each): ~55/s
 * Tier 2 all bought (L1 each): ~160/s
 * Tier 3 all bought (L1 each): ~330/s
 */
public class GymSessionManager {

    /** Flat income every gym earns regardless of upgrades. */
    public static final int BASE_INCOME      = 5;

    private static final int TICKS_PER_SECOND = 20;
    private static int tickAccumulator        = 0;

    // ── Income calculation ────────────────────────────────────────────────────

    /**
     * Recomputes income from scratch by summing all owned upgrade bonuses.
     * Call this after every upgrade purchase and when opening the gym.
     * Stores the result directly into {@code data.incomePerSecond}.
     *
     * @return the new income per second value
     */
    public static int recalculateIncome(PlayerData data) {
        int income = BASE_INCOME;

        for (JsonUpgradeDefinition def : UpgradeRegistry.getAll().values()) {
            int level = data.getUpgradeLevel(def.id);
            if (level <= 0 || def.effects == null) continue;
            income += def.effects.incomeBonus * level;
        }

        // Flat income bonus granted permanently on reaching each tier
        income += getTierIncomeBonus(data.tierLevel);

        data.incomePerSecond = income;
        return income;
    }

    /** Flat income bonus ($/s) added permanently when a tier is reached. */
    public static int getTierIncomeBonus(int tier) {
        return switch (tier) {
            case 2 -> 100;
            case 3 -> 200;   // cumulative: T3 players also had T2 upgrade, so delta is +100 more
            default -> 0;
        };
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    /**
     * Registered in TycoonCore via ServerTickEvents.
     * Fires every server tick; distributes income exactly once per second.
     *
     * For each online player whose gym is open:
     *   • Add data.incomePerSecond to their scoreboard
     *   • Show action bar: "+$X/s"
     */
    public static void tick(MinecraftServer server) {
        if (++tickAccumulator < TICKS_PER_SECOND) return;
        tickAccumulator = 0;

        String sym = GymCoreConfigLoader.get().currencySymbol;
        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerData data = manager.getOrCreate(player.getUuid());

            if (!data.gymActive) continue;

            // ── Sabotage blocks income ────────────────────────────────────────
            if (SabotageManager.isActive(player.getUuid())) {
                // If exercising, the fitness action bar will show the blocked state
                if (!ExerciseManager.isExercising(player.getUuid())) {
                    player.sendMessage(
                        Text.translatable("tycoon.income.blocked"),
                        true   // action bar
                    );
                }
                continue;
            }

            int income = data.incomePerSecond;
            if (income <= 0) continue;

            boolean added = ScoreboardEconomy.addMoney(player, income);

            if (added) {
                // If exercising, skip — the fitness action bar handles the combined display
                if (!ExerciseManager.isExercising(player.getUuid())) {
                    player.sendMessage(
                        Text.translatable("tycoon.income.tick", sym + formatMoney(income)),
                        true   // true = action bar (not chat)
                    );
                }
            } else {
                player.sendMessage(
                    Text.translatable("tycoon.income.no_scoreboard"),
                    true
                );
            }
        }
    }

    // ── Formatting ────────────────────────────────────────────────────────────

    public static String formatMoney(long amount) {
        if (amount < 1_000) return String.valueOf(amount);
        StringBuilder sb = new StringBuilder(Long.toString(amount));
        for (int i = sb.length() - 3; i > 0; i -= 3) sb.insert(i, ',');
        return sb.toString();
    }
}
