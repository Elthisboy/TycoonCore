package com.elthisboy.tycooncore;

import com.elthisboy.tycooncore.action.ActionProcessor;
import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.command.PcCommand;
import com.elthisboy.tycooncore.command.TycoonCommand;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.equipment.ExerciseConfigLoader;
import com.elthisboy.tycooncore.equipment.ExerciseManager;
import com.elthisboy.tycooncore.supervisor.SupervisorManager;
import com.elthisboy.tycooncore.fitness.FitnessConfigLoader;
import com.elthisboy.tycooncore.fitness.FitnessManager;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.gym.NightGymManager;
import com.elthisboy.tycooncore.income.PassiveIncomeScheduler;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.registry.ModBlocks;
import com.elthisboy.tycooncore.registry.ModItems;
import com.elthisboy.tycooncore.registry.ModScreenHandlers;
import com.elthisboy.tycooncore.upgrade.UpgradeRegistry;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackBlockCallback;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TycoonCore implements ModInitializer {

    public static final String MOD_ID = "tycooncore";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("[TycoonCore] Starting initialisation...");

        // ── 1. Load config first (other systems read from it) ─────────────────
        GymCoreConfigLoader.load();
        ExerciseConfigLoader.reload();
        FitnessConfigLoader.reload();
        LOGGER.info("[TycoonCore] Config loaded. Scoreboard: '{}', Currency: '{}'",
            GymCoreConfigLoader.get().scoreboardName,
            GymCoreConfigLoader.get().currencySymbol);

        // ── 2. Load upgrade registry from JSON files ───────────────────────────
        UpgradeRegistry.load();
        LOGGER.info("[TycoonCore] Upgrade registry loaded ({} upgrades, {} categories).",
            UpgradeRegistry.size(), UpgradeRegistry.getCategories().size());

        // ── 3. Register blocks, items + screen handlers ───────────────────────
        ModScreenHandlers.initialize();
        ModBlocks.initialize();
        ModItems.initialize();

        // ── 4. Register network payloads + receivers ──────────────────────────
        NetworkHandler.registerPayloads();
        NetworkHandler.registerReceivers();

        // ── 5. Passive income ticker (config-driven, auto-interval) ───────────
        PassiveIncomeScheduler.register();

        // ── 6. Sabotage tick: technician arrival + timer expiry ───────────────
        ServerTickEvents.END_SERVER_TICK.register(SabotageManager::tick);

        // ── 7. Gym session tick: per-second income for open gyms ──────────────
        ServerTickEvents.END_SERVER_TICK.register(GymSessionManager::tick);

        // ── 7b. Night cycle: auto-close all gyms at nightfall ─────────────────
        ServerTickEvents.END_SERVER_TICK.register(NightGymManager::tick);

        // ── 7c. Gym equipment: animation, sound, position lock ─────────────────
        ServerTickEvents.END_SERVER_TICK.register(ExerciseManager::tick);

        // ── 7e. Supervisor approval: countdown tick ────────────────────────────
        ServerTickEvents.END_SERVER_TICK.register(SupervisorManager::tick);

        // ── 7d. Any click during WEIGHT_LIFT or TREADMILL_WALK → stop ────────
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            var state = ExerciseManager.getState(sp.getUuid());
            if (state == null) return ActionResult.PASS;
            var t = state.type;
            if (t != com.elthisboy.tycooncore.equipment.EquipmentType.WEIGHT_LIFT
                    && t != com.elthisboy.tycooncore.equipment.EquipmentType.TREADMILL_WALK) {
                return ActionResult.PASS;
            }
            ExerciseManager.stopExercise(sp);
            return ActionResult.FAIL;
        });

        AttackBlockCallback.EVENT.register((player, world, hand, pos, direction) -> {
            if (world.isClient() || !(player instanceof ServerPlayerEntity sp)) {
                return ActionResult.PASS;
            }
            var state = ExerciseManager.getState(sp.getUuid());
            if (state == null) return ActionResult.PASS;
            var t = state.type;
            if (t != com.elthisboy.tycooncore.equipment.EquipmentType.WEIGHT_LIFT
                    && t != com.elthisboy.tycooncore.equipment.EquipmentType.TREADMILL_WALK) {
                return ActionResult.PASS;
            }
            ExerciseManager.stopExercise(sp);
            return ActionResult.FAIL;
        });

        // ── 8. Register command trees ─────────────────────────────────────────
        PcCommand.register();      // /pc event <sabotage|recovery|mission>
        TycoonCommand.register();  // /tycoon gym <start|end|status>

        // ── 9. Player join: restore bossbar, push data + upgrade meta ─────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            var player = handler.player;
            PlayerDataManager manager = PlayerDataManager.getOrCreate(server);
            PlayerData data = manager.getOrCreate(player.getUuid());

            BossbarManager.onPlayerJoin(player, data);
            // If the player joins during the night with the gym open, close it
            NightGymManager.closeIfNight(server, player);
            NetworkHandler.sendPlayerData(player, data);
            FitnessManager.syncOnJoin(player, data);
            NetworkHandler.sendUpgradeMeta(player);
            // Re-apply all persistent stat effects (potion, attribute) after login
            ActionProcessor.reapplyPersistentEffects(player, data);
            // Push sabotage state so client overlay reflects server state immediately
            NetworkHandler.sendSabotageState(player, SabotageManager.getState(player.getUuid()));
        });

        // ── 10. Player disconnect: clean up bossbar + exercise state ─────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            BossbarManager.remove(handler.player.getUuid());
            // Release the equipment block entity slot without sending network packets
            ExerciseManager.forceStop(handler.player.getUuid(),
                handler.player.getServerWorld());
            // Cancel any pending supervisor approval (as target or supervisor)
            SupervisorManager.cancelApproval(handler.player.getUuid());
        });

        LOGGER.info("[TycoonCore] Initialisation complete.");
    }
}
