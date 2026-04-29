package com.elthisboy.tycooncore.command;

import com.elthisboy.tycooncore.config.GymCoreConfig;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.fitness.FitnessManager;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.gym.NightGymManager;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.supervisor.SupervisorManager;
import com.elthisboy.tycooncore.tier.TierManager;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * /tycoon gym <start | end | status> [player]
 *
 * Without [player]: targets the executing player (level 0 required).
 * With    [player]: accepts @p / player name; requires level 2 so command
 *                   blocks (which run at level 2) can use entity selectors.
 *
 * State persists in PlayerData across rejoins and server restarts.
 */
public class TycoonCommand {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(

                literal("tycoon")

                    // ── /tycoon reset [player]  (OP only) ───────────────────
                    .then(literal("reset")
                        .requires(src -> src.hasPermissionLevel(2))

                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> executeReset(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target")))
                        )

                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendError(Text.literal(
                                    "[TycoonCore] Run as a player or provide a target."));
                                return 0;
                            }
                            return executeReset(ctx.getSource(), player);
                        })
                    )

                    // ── /tycoon tier_up [player]  (OP only, for testing) ─────
                    .then(literal("tier_up")
                        .requires(src -> src.hasPermissionLevel(2))

                        .then(argument("target", EntityArgumentType.player())
                            .executes(ctx -> executeTierUp(
                                ctx.getSource(),
                                EntityArgumentType.getPlayer(ctx, "target")))
                        )

                        .executes(ctx -> {
                            ServerPlayerEntity player = ctx.getSource().getPlayer();
                            if (player == null) {
                                ctx.getSource().sendError(Text.literal(
                                    "[TycoonCore] Run as a player or provide a target."));
                                return 0;
                            }
                            return executeTierUp(ctx.getSource(), player);
                        })
                    )

                    .then(literal("gym")

                        // ── /tycoon gym start [player] ────────────────────────
                        .then(literal("start")

                            // Level-2 branch: /tycoon gym start @p  (command blocks / OPs)
                            .then(argument("target", EntityArgumentType.player())
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> executeStart(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "target")))
                            )

                            // Level-0 branch: /tycoon gym start  (player runs for themselves)
                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    ctx.getSource().sendError(Text.translatable(
                                        "tycoon.cmd.no_player", "start"));
                                    return 0;
                                }
                                return executeStart(ctx.getSource(), player);
                            })
                        )

                        // ── /tycoon gym end [player] ──────────────────────────
                        .then(literal("end")

                            .then(argument("target", EntityArgumentType.player())
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> executeEnd(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "target")))
                            )

                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    ctx.getSource().sendError(Text.translatable(
                                        "tycoon.cmd.no_player", "end"));
                                    return 0;
                                }
                                return executeEnd(ctx.getSource(), player);
                            })
                        )

                        // ── /tycoon gym status [player] ───────────────────────
                        .then(literal("status")

                            .then(argument("target", EntityArgumentType.player())
                                .requires(src -> src.hasPermissionLevel(2))
                                .executes(ctx -> executeStatus(
                                    ctx.getSource(),
                                    EntityArgumentType.getPlayer(ctx, "target")))
                            )

                            .executes(ctx -> {
                                ServerPlayerEntity player = ctx.getSource().getPlayer();
                                if (player == null) {
                                    ctx.getSource().sendError(Text.translatable(
                                        "tycoon.cmd.no_player", "status"));
                                    return 0;
                                }
                                return executeStatus(ctx.getSource(), player);
                            })
                        )
                    )
            )
        );
    }

    // ── Subcommand implementations ─────────────────────────────────────────────

    private static int executeStart(ServerCommandSource src, ServerPlayerEntity player) {
        // Block opening at night
        if (NightGymManager.isNight(player.getServer())) {
            player.sendMessage(Text.translatable("tycoon.night.blocked"), false);
            return 0;
        }

        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        if (data.gymActive) {
            player.sendMessage(Text.translatable("tycoon.gym.already_open"), false);
            return 0;
        }

        int income = GymSessionManager.recalculateIncome(data);
        data.gymActive = true;
        manager.set(player.getUuid(), data);
        NetworkHandler.sendPlayerData(player, data);

        String sym = GymCoreConfigLoader.get().currencySymbol;
        player.sendMessage(Text.translatable("tycoon.gym.opened",
            sym + GymSessionManager.formatMoney(income)), false);

        return 1;
    }

    private static int executeEnd(ServerCommandSource src, ServerPlayerEntity player) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        if (!data.gymActive) {
            player.sendMessage(Text.translatable("tycoon.gym.already_closed"), false);
            return 0;
        }

        data.gymActive = false;
        manager.set(player.getUuid(), data);
        NetworkHandler.sendPlayerData(player, data);

        player.sendMessage(Text.translatable("tycoon.gym.closed"), false);
        return 1;
    }

    private static int executeTierUp(ServerCommandSource src, ServerPlayerEntity player) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        if (data.tierLevel >= TierManager.MAX_TIER) {
            src.sendError(Text.literal("[TycoonCore] " + player.getName().getString()
                + " is already at max tier (" + TierManager.MAX_TIER + ")."));
            return 0;
        }

        // Force advance — bypass supervisor and progress requirements
        data.tierLevel++;
        data.upgradeProgress     = 0;
        data.pendingTierApproval = false;
        data.gymActive           = false;
        GymSessionManager.recalculateIncome(data);
        manager.set(player.getUuid(), data);

        // Cancel any pending approval countdown for this player
        SupervisorManager.cancelApproval(player.getUuid());

        // Teleport to the tier location (same as supervisor path)
        GymCoreConfig.TierLocation loc =
            GymCoreConfigLoader.get().tierLocations.get(data.tierLevel);
        if (loc != null) {
            player.requestTeleport(loc.x, loc.y, loc.z);
            player.setYaw(loc.yaw);
        }

        // Same bonuses as the supervisor path
        FitnessManager.addPoints(player, 100);
        SupervisorManager.runTierBonusCommands(player, data.tierLevel, src.getServer());

        // Configured approval commands (placeholders: %player%, %supervisor%, %tier%)
        var approvalCmds = GymCoreConfigLoader.get().supervisorApprovalCommands
            .get(data.tierLevel);
        if (approvalCmds != null && src.getServer() != null) {
            var cmdSrc = src.getServer().getCommandSource().withLevel(4).withSilent();
            String name = player.getName().getString();
            for (String cmd : approvalCmds) {
                String resolved = cmd
                    .replace("%player%",     name)
                    .replace("%supervisor%", name)  // no supervisor in force-up
                    .replace("%tier%",       String.valueOf(data.tierLevel));
                src.getServer().getCommandManager().executeWithPrefix(cmdSrc, resolved);
            }
        }

        BossbarManager.update(player, data);
        NetworkHandler.sendPlayerData(player, data);
        NetworkHandler.sendUpgradeMeta(player);

        player.sendMessage(Text.translatable("pc.tier_up", data.tierLevel), false);
        src.sendFeedback(() -> Text.literal("[TycoonCore] Force-advanced "
            + player.getName().getString() + " to Tier " + data.tierLevel + "."), true);

        return 1;
    }

    private static int executeReset(ServerCommandSource src, ServerPlayerEntity player) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        // Clear all gym PC progress
        data.upgradeLevels.clear();
        data.upgradeProgress    = 0;
        data.pendingTierApproval = false;
        data.tierLevel          = 1;
        data.gymActive          = false;
        GymSessionManager.recalculateIncome(data);
        manager.set(player.getUuid(), data);

        // Cancel any pending supervisor countdown
        SupervisorManager.cancelApproval(player.getUuid());

        // Sync client
        BossbarManager.update(player, data);
        NetworkHandler.sendPlayerData(player, data);
        NetworkHandler.sendUpgradeMeta(player);

        player.sendMessage(Text.literal("§cTu progreso del gimnasio ha sido reiniciado."), false);
        src.sendFeedback(() -> Text.literal("[TycoonCore] Reset gym progress for "
            + player.getName().getString() + "."), true);

        return 1;
    }

    private static int executeStatus(ServerCommandSource src, ServerPlayerEntity player) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(player.getServer());
        PlayerData data = manager.getOrCreate(player.getUuid());

        int    income = GymSessionManager.recalculateIncome(data);
        String sym    = GymCoreConfigLoader.get().currencySymbol;
        String incStr = sym + GymSessionManager.formatMoney(income);

        player.sendMessage(data.gymActive
            ? Text.translatable("tycoon.gym.status_open",  incStr)
            : Text.translatable("tycoon.gym.status_closed", incStr), false);

        return 1;
    }
}
