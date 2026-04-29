package com.elthisboy.tycooncore.upgrade;

import com.elthisboy.tycooncore.action.ActionProcessor;
import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.tier.TierManager;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Core upgrade pipeline:
 *
 *   validate → deduct money → fire actions → set level →
 *   recalculate income → update tier → sync client
 *
 * Step 5 (recalculate income) is new: after every purchase the cached
 * income_per_second in PlayerData is refreshed so the PC footer and
 * the gym tick loop always reflect the latest rate.
 */
public class UpgradeManager {

    public static UpgradeResult tryPurchase(ServerPlayerEntity player, String upgradeId) {

        // ── Look up definition ────────────────────────────────────────────────
        JsonUpgradeDefinition def = UpgradeRegistry.get(upgradeId);
        if (def == null)
            return UpgradeResult.failure(Text.translatable("pc.error.unknown_upgrade"));

        if (player.getServer() == null)
            return UpgradeResult.failure(Text.translatable("pc.error.server_unavailable"));

        // ── Scoreboard sanity check ───────────────────────────────────────────
        if (!ScoreboardEconomy.objectiveExists(player))
            return UpgradeResult.failure(Text.translatable(
                "pc.error.scoreboard_not_found",
                GymCoreConfigLoader.get().scoreboardName));

        // ── Load player data ──────────────────────────────────────────────────
        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());
        int currentLevel = data.getUpgradeLevel(def.id);

        // ── 1. Validate ───────────────────────────────────────────────────────
        UpgradeResult validation = validate(def, data, currentLevel, player);
        if (!validation.success()) return validation;

        long cost = def.getCostForLevel(currentLevel);

        // ── 2. Deduct money ───────────────────────────────────────────────────
        if (!ScoreboardEconomy.deductMoney(player, cost))
            return UpgradeResult.failure(Text.translatable("pc.error.not_enough_money"));

        // ── 3. Fire world actions ─────────────────────────────────────────────
        ActionProcessor.process(player, def.actions, currentLevel + 1);

        // ── 3b. Global command — runs on every Tier 2 upgrade purchase ─────────
        if (def.requiredTier == 2 && player.getServer() != null) {
            var globalSrc = player.getServer().getCommandSource()
                .withLevel(4).withSilent();
            player.getServer().getCommandManager().executeWithPrefix(globalSrc,
                "fill 233 -35 91 211 -25 143 air replace refurbished_furniture:oak_crate");
        }

        // ── 4. Persist new level ──────────────────────────────────────────────
        data.setUpgradeLevel(def.id, currentLevel + 1);

        // ── 5. Recalculate income_per_second ──────────────────────────────────
        // Always recalculate from scratch to keep the cached value accurate.
        int oldIncome  = data.incomePerSecond;
        int newIncome  = GymSessionManager.recalculateIncome(data); // also sets data.incomePerSecond
        int incomeDiff = newIncome - oldIncome;

        if (incomeDiff > 0) {
            String sym = GymCoreConfigLoader.get().currencySymbol;
            player.sendMessage(
                Text.translatable("tycoon.income.increase",
                    incomeDiff, sym, sym + newIncome),
                false);
        }

        // ── 6. Update tier progression ────────────────────────────────────────
        if (def.countsForProgress && data.tierLevel < TierManager.MAX_TIER) {
            data.upgradeProgress++;
            int required = TierManager.getRequiredUpgrades(data.tierLevel);

            // Reached the threshold → flag for supervisor approval instead of auto-advancing
            if (data.upgradeProgress >= required && !data.pendingTierApproval) {
                data.pendingTierApproval = true;
                player.sendMessage(Text.translatable("pc.tier_ready"), false);
            }
        }

        // ── 7. Persist + sync ─────────────────────────────────────────────────
        manager.set(player.getUuid(), data);
        BossbarManager.update(player, data);
        NetworkHandler.sendPlayerData(player, data);

        return UpgradeResult.success(
            Text.translatable("pc.upgrade.purchased",
                Text.translatable(def.getNameKey()), currentLevel + 1));
    }

    // ── Validation ────────────────────────────────────────────────────────────

    private static UpgradeResult validate(JsonUpgradeDefinition def, PlayerData data,
                                           int currentLevel, ServerPlayerEntity player) {
        if (data.tierLevel < def.requiredTier)
            return UpgradeResult.failure(
                Text.translatable("pc.error.requires_tier", def.requiredTier));

        if (currentLevel >= def.maxLevel)
            return UpgradeResult.failure(Text.translatable("pc.error.max_level"));

        long cost = def.getCostForLevel(currentLevel);
        if (!ScoreboardEconomy.hasMoney(player, cost))
            return UpgradeResult.failure(Text.translatable("pc.error.not_enough_money"));

        return UpgradeResult.ok();
    }
}
