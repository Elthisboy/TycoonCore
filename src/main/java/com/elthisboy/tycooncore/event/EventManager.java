package com.elthisboy.tycooncore.event;

import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.tier.TierManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Handles PC-driven events: sabotage, recovery, and special missions.
 * These can be triggered externally (e.g. by map scripts or command blocks)
 * to interact with the PC progression/economy system.
 */
public class EventManager {

    public enum EventType {
        SABOTAGE,
        RECOVERY,
        SPECIAL_MISSION
    }

    /**
     * Sabotage: penalises the player by reducing their money.
     *
     * @param penalty amount of money to deduct (clamped to 0)
     */
    public static void applySabotage(ServerPlayerEntity player, long penalty) {
        String sym = GymCoreConfigLoader.get().currencySymbol;

        long current = ScoreboardEconomy.getMoney(player);
        long after   = Math.max(0L, current - penalty);
        ScoreboardEconomy.setMoney(player, after);

        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());
        persist(manager, player, data);

        player.sendMessage(Text.literal("§c[Event] §7Sabotage! Lost §c" + sym + penalty), false);
    }

    /**
     * Recovery: grants bonus money to the player.
     *
     * @param bonus amount of money to add
     */
    public static void applyRecovery(ServerPlayerEntity player, long bonus) {
        String sym = GymCoreConfigLoader.get().currencySymbol;

        ScoreboardEconomy.addMoney(player, bonus);

        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());
        persist(manager, player, data);

        player.sendMessage(Text.literal("§a[Event] §7Recovery! Gained §a" + sym + bonus), false);
    }

    /**
     * Special Mission: grants money and bonus upgrade progress (may trigger a tier-up).
     *
     * @param reward        money reward
     * @param progressBonus extra upgrade progress points to add
     */
    public static void applySpecialMission(ServerPlayerEntity player, long reward, int progressBonus) {
        String sym = GymCoreConfigLoader.get().currencySymbol;

        ScoreboardEconomy.addMoney(player, reward);

        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        int cap = TierManager.getRequiredUpgrades(data.tierLevel);
        data.upgradeProgress = Math.min(data.upgradeProgress + progressBonus, cap);
        boolean tieredUp = TierManager.checkAndAdvanceTier(data);

        persist(manager, player, data);

        player.sendMessage(Text.literal(
            "§e[Event] §7Mission complete! Earned §e" + sym + reward +
            (progressBonus > 0 ? " §7and §a+" + progressBonus + " progress" : "")), false);

        if (tieredUp) {
            player.sendMessage(Text.literal(
                "§6[TycoonCore] §eTier advanced to " + data.tierLevel + "!"), false);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────

    private static void persist(PlayerDataManager manager, ServerPlayerEntity player, PlayerData data) {
        manager.set(player.getUuid(), data);
        BossbarManager.update(player, data);
        NetworkHandler.sendPlayerData(player, data);
    }
}
