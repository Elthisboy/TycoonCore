package com.elthisboy.tycooncore.network;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.config.GymCoreConfig;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.elthisboy.tycooncore.event.SabotageState;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.gym.NightGymManager;
import net.minecraft.server.MinecraftServer;
import com.elthisboy.tycooncore.network.packet.ExerciseStatePayload;
import com.elthisboy.tycooncore.network.packet.GymTogglePayload;
import com.elthisboy.tycooncore.network.packet.PlayerDataSyncPayload;
import com.elthisboy.tycooncore.network.packet.RequestSyncPayload;
import com.elthisboy.tycooncore.network.packet.SabotageActionPayload;
import com.elthisboy.tycooncore.network.packet.SabotageStatePayload;
import com.elthisboy.tycooncore.network.packet.UpgradeClientEntry;
import com.elthisboy.tycooncore.network.packet.UpgradeMetaSyncPayload;
import com.elthisboy.tycooncore.network.packet.UpgradeRequestPayload;
import com.elthisboy.tycooncore.upgrade.UpgradeManager;
import com.elthisboy.tycooncore.upgrade.UpgradeRegistry;
import com.elthisboy.tycooncore.upgrade.UpgradeResult;
import com.elthisboy.tycooncore.upgrade.json.JsonUpgradeDefinition;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

/**
 * Single registration point for all custom network payloads.
 * Must be called during mod initialisation (before the server starts).
 */
public class NetworkHandler {

    // ── Registration ──────────────────────────────────────────────────────────

    public static void registerPayloads() {
        // Client → Server
        PayloadTypeRegistry.playC2S().register(
            UpgradeRequestPayload.ID, UpgradeRequestPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
            SabotageActionPayload.ID, SabotageActionPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
            GymTogglePayload.ID, GymTogglePayload.CODEC);
        PayloadTypeRegistry.playC2S().register(
            RequestSyncPayload.ID, RequestSyncPayload.CODEC);

        // Server → Client
        PayloadTypeRegistry.playS2C().register(
            PlayerDataSyncPayload.ID, PlayerDataSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
            UpgradeMetaSyncPayload.ID, UpgradeMetaSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
            SabotageStatePayload.ID, SabotageStatePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(
            ExerciseStatePayload.ID, ExerciseStatePayload.CODEC);
    }

    public static void registerReceivers() {
        // ── Upgrade purchase ──────────────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
            UpgradeRequestPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                UpgradeResult result = UpgradeManager.tryPurchase(player, payload.upgradeId());
                if (!result.success()) {
                    player.sendMessage(
                        Text.literal("§c[TycoonCore] §r").append(result.message()),
                        false);
                }
            })
        );

        // ── Sabotage resolution action ────────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
            SabotageActionPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                switch (payload.action().toLowerCase()) {
                    case "pay"        -> SabotageManager.resolvePay(player);
                    case "item"       -> SabotageManager.resolveItem(player);
                    case "technician" -> SabotageManager.callTechnician(player);
                    default           -> player.sendMessage(
                        Text.translatable("tycoon.error.unknown_action", payload.action()),
                        false);
                }
            })
        );

        // ── PC boot: client requests a fresh data snapshot ───────────────────
        ServerPlayNetworking.registerGlobalReceiver(
            RequestSyncPayload.ID,
            (payload, context) -> context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                sendPlayerData(player,
                    PlayerDataManager.getOrCreate(player.getServer())
                                     .getOrCreate(player.getUuid()));
                sendUpgradeMeta(player);
            })
        );

        // ── Gym toggle (PC footer button) ─────────────────────────────────────
        ServerPlayNetworking.registerGlobalReceiver(
            GymTogglePayload.ID,
            (payload, context) -> context.server().execute(() -> {
                ServerPlayerEntity player = context.player();
                PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
                PlayerData data = manager.getOrCreate(player.getUuid());

                // Block opening at night — closing is always allowed
                boolean tryingToOpen = !data.gymActive;
                if (tryingToOpen && NightGymManager.isNight(context.server())) {
                    player.sendMessage(Text.translatable("tycoon.night.blocked"), false);
                    return;
                }

                // Toggle and recalculate income when opening
                data.gymActive = !data.gymActive;
                if (data.gymActive) {
                    GymSessionManager.recalculateIncome(data);
                }
                manager.set(player.getUuid(), data);
                sendPlayerData(player, data);   // pushes updated gymActive + incomePerSecond

                String sym = GymCoreConfigLoader.get().currencySymbol;
                if (data.gymActive) {
                    player.sendMessage(Text.translatable("tycoon.gym.opened",
                        sym + GymSessionManager.formatMoney(data.incomePerSecond)), false);
                } else {
                    player.sendMessage(Text.translatable("tycoon.gym.closed"), false);
                }

                // Run per-tier commands for open/close
                runTierCommands(player, data, context.server(), data.gymActive);
            })
        );
    }

    // ── Outbound helpers ──────────────────────────────────────────────────────

    /**
     * Pushes a full player-data snapshot to the client.
     * Money is read from the scoreboard at call time.
     */
    public static void sendPlayerData(ServerPlayerEntity player, PlayerData data) {
        long money = ScoreboardEconomy.getMoney(player);
        ServerPlayNetworking.send(player, new PlayerDataSyncPayload(data, money));
    }

    /**
     * Sends the full upgrade catalogue metadata to the client.
     * Call once on join and again after a live upgrade-config reload.
     */
    public static void sendUpgradeMeta(ServerPlayerEntity player) {
        String symbol = GymCoreConfigLoader.get().currencySymbol;

        // Only send upgrades that belong to the player's CURRENT tier
        int playerTier = PlayerDataManager.getOrCreate(player.getServer())
            .getOrCreate(player.getUuid()).tierLevel;

        List<UpgradeClientEntry> entries = new ArrayList<>();
        for (JsonUpgradeDefinition def : UpgradeRegistry.getAll().values()) {
            if (def.requiredTier != playerTier) continue;
            entries.add(new UpgradeClientEntry(
                def.id,
                def.category,
                def.maxLevel,
                def.requiredTier,
                def.baseCost,
                def.costMultiplier,
                def.countsForProgress,
                def.effects != null ? def.effects.incomeBonus : 0
            ));
        }

        ServerPlayNetworking.send(player, new UpgradeMetaSyncPayload(symbol, entries));
    }

    /**
     * Sends the current sabotage state snapshot to the client.
     * Called on sabotage start, technician call, resolution, and expiry.
     */
    public static void sendSabotageState(ServerPlayerEntity player, SabotageState state) {
        ServerPlayNetworking.send(player, new SabotageStatePayload(
            state.active,
            state.durationSeconds,
            state.penaltyAmount,
            state.startTimeMs,
            state.technicianCallTimeMs
        ));
    }

    // ── Tier command execution ─────────────────────────────────────────────────

    /**
     * Runs the configured on_open or on_close commands for the player's current tier.
     * Commands execute at server level 4 (operator), so any vanilla command is allowed.
     * Supported placeholders: %player% → player name, %tier% → tier number.
     *
     * @param opened true when the gym just opened, false when it just closed
     */
    private static void runTierCommands(ServerPlayerEntity player, PlayerData data,
                                         MinecraftServer server, boolean opened) {
        GymCoreConfig.TierCommands cmds =
            GymCoreConfigLoader.get().tierCommands.get(data.tierLevel);
        if (cmds == null) return;

        List<String> commands = opened ? cmds.onOpen : cmds.onClose;
        if (commands == null || commands.isEmpty()) return;

        var cmdManager = server.getCommandManager();
        var src = server.getCommandSource().withLevel(4);
        String playerName = player.getName().getString();
        String tier = String.valueOf(data.tierLevel);

        for (String cmd : commands) {
            try {
                String resolved = cmd
                    .replace("%player%", playerName)
                    .replace("%tier%",   tier);
                cmdManager.executeWithPrefix(src, resolved);
            } catch (Exception e) {
                TycoonCore.LOGGER.warn("[TycoonCore] Tier command failed '{}': {}",
                    cmd, e.getMessage());
            }
        }
    }
}
