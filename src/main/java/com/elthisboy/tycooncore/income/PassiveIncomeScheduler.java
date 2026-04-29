package com.elthisboy.tycooncore.income;

import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.tier.TierManager;
import com.elthisboy.tycooncore.upgrade.UpgradeRegistry;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.registry.Registries;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Random;

/**
 * Periodically grants passive income and applies ongoing gameplay bonuses to
 * every online player.
 *
 * Income formula (per spec §10):
 *   final_income = (base + passiveIncomeBonus) * incomeMultiplier * tierMultiplier
 *
 * Side-effects fired on the same cycle:
 *   clientFlowBonus      → grants XP proportional to the total bonus across all
 *                          owned upgrades; makes the XP bar visibly grow per cycle
 *   negativeEffectReduction → probability of clearing one active negative status
 *                             effect; higher reduction = more reliable cleanse
 *
 * All values are data-driven (JSON effects block) – no hardcoded numbers here.
 */
public class PassiveIncomeScheduler {

    private static final long   BASE_INCOME = 0L;
    private static final Random RNG         = new Random();

    private static int tickCounter = 0;

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(PassiveIncomeScheduler::onTick);
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    private static void onTick(MinecraftServer server) {
        if (!GymCoreConfigLoader.get().enablePassiveIncome) {
            tickCounter = 0;
            return;
        }
        if (++tickCounter < GymCoreConfigLoader.get().passiveIncomeIntervalTicks) return;
        tickCounter = 0;

        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerData data = manager.getOrCreate(player.getUuid());

            // ── Passive income (blocked during active sabotage) ───────────────
            long income = calculateIncome(data);
            if (income > 0 && !SabotageManager.isActive(player.getUuid())) {
                if (ScoreboardEconomy.addMoney(player, income)) {
                    BossbarManager.update(player, data);
                    NetworkHandler.sendPlayerData(player, data);
                }
            }

            // ── XP from client flow bonus ─────────────────────────────────────
            double flowBonus = calculateClientFlowBonus(data);
            if (flowBonus > 0) {
                int xp = (int) Math.round(flowBonus * TierManager.getTierMultiplier(data.tierLevel));
                if (xp > 0) player.addExperience(xp);
            }

            // ── Negative effect cleanse ────────────────────────────────────────
            double reduction = calculateNegativeReduction(data);
            if (reduction > 0) {
                tryCleanseNegativeEffect(player, reduction);
            }
        }
    }

    // ── Income formula ────────────────────────────────────────────────────────

    public static long calculateIncome(PlayerData data) {
        double base       = BASE_INCOME;
        double multiplier = 1.0;

        for (JsonUpgradeDefinition def : UpgradeRegistry.getAll().values()) {
            int level = data.getUpgradeLevel(def.id);
            if (level <= 0 || def.effects == null) continue;
            base       += def.effects.passiveIncomeBonus * level;
            multiplier += def.effects.incomeMultiplier   * level;
        }

        if (base <= 0) return 0L;
        double tierMult = TierManager.getTierMultiplier(data.tierLevel);
        return Math.max(0L, (long)(base * multiplier * tierMult));
    }

    // ── Client flow bonus (→ XP) ──────────────────────────────────────────────

    private static double calculateClientFlowBonus(PlayerData data) {
        double total = 0.0;
        for (JsonUpgradeDefinition def : UpgradeRegistry.getAll().values()) {
            int level = data.getUpgradeLevel(def.id);
            if (level <= 0 || def.effects == null) continue;
            total += def.effects.clientFlowBonus * level;
        }
        return total;
    }

    // ── Negative effect reduction ─────────────────────────────────────────────

    private static double calculateNegativeReduction(PlayerData data) {
        double total = 0.0;
        for (JsonUpgradeDefinition def : UpgradeRegistry.getAll().values()) {
            int level = data.getUpgradeLevel(def.id);
            if (level <= 0 || def.effects == null) continue;
            total += def.effects.negativeEffectReduction * level;
        }
        return total;
    }

    /**
     * With a probability of min(1.0, reductionValue), removes one active negative
     * status effect from the player.  Higher reduction values make this more
     * reliable; a value >= 1.0 always cleanses on every income cycle.
     */
    private static void tryCleanseNegativeEffect(ServerPlayerEntity player, double reduction) {
        double chance = Math.min(1.0, reduction);
        if (RNG.nextDouble() >= chance) return;

        Collection<StatusEffectInstance> active = player.getStatusEffects();
        List<RegistryEntry<StatusEffect>> negative = new ArrayList<>();

        for (StatusEffectInstance instance : active) {
            // Category HARMFUL covers all vanilla negative effects
            if (instance.getEffectType().value().getCategory()
                    == net.minecraft.entity.effect.StatusEffectCategory.HARMFUL) {
                negative.add(instance.getEffectType());
            }
        }

        if (!negative.isEmpty()) {
            RegistryEntry<StatusEffect> toRemove = negative.get(RNG.nextInt(negative.size()));
            player.removeStatusEffect(toRemove);
        }
    }
}
