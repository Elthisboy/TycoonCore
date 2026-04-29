package com.elthisboy.tycooncore.supervisor;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.bossbar.BossbarManager;
import com.elthisboy.tycooncore.config.GymCoreConfig;
import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.fitness.FitnessManager;
import com.elthisboy.tycooncore.gym.GymSessionManager;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.tier.TierManager;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Manages supervisor-initiated tier-up approvals.
 *
 * ── Flow ──────────────────────────────────────────────────────────────────────
 *   1. Supervisor right-clicks target player with the supervisor item.
 *   2. SupervisorItem calls {@link #startApproval} after validating pendingTierApproval.
 *   3. Every server tick {@link #tick} decrements the countdown for each pending approval,
 *      broadcasting the remaining seconds to both players via action bar.
 *   4. When the countdown reaches zero, {@link #complete} fires:
 *      - Advances the target's tier via TierManager
 *      - Grants +100 fitness points
 *      - Teleports both to the configured tier location (if set)
 *      - Executes supervisorApprovalCommands for the new tier
 *      - Refreshes the upgrade catalogue for the target
 */
public class SupervisorManager {

    // ── Pending approvals ─────────────────────────────────────────────────────

    private record Approval(UUID supervisorUuid, int totalTicks, int remainingTicks) {
        Approval decrement() {
            return new Approval(supervisorUuid, totalTicks, remainingTicks - 1);
        }
    }

    /** key = target player UUID */
    private static final Map<UUID, Approval> PENDING = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Initiates a tier-up approval countdown for {@code target}.
     * If an approval is already in progress for this player it is cancelled first.
     *
     * @param supervisor the player holding the supervisor item
     * @param target     the gym owner whose tier-up is being approved
     */
    public static void startApproval(ServerPlayerEntity supervisor, ServerPlayerEntity target) {
        UUID targetUuid = target.getUuid();

        // Cancel any existing approval for this player
        if (PENDING.containsKey(targetUuid)) {
            PENDING.remove(targetUuid);
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.restarted",
                target.getName()), false);
        }

        int seconds = Math.max(1, GymCoreConfigLoader.get().supervisorCountdownSeconds);
        int ticks   = seconds * 20;

        PENDING.put(targetUuid, new Approval(supervisor.getUuid(), ticks, ticks));

        // Notify both parties
        supervisor.sendMessage(Text.translatable("tycoon.supervisor.started_supervisor",
            target.getName(), seconds), false);
        target.sendMessage(Text.translatable("tycoon.supervisor.started_target",
            supervisor.getName(), seconds), false);
    }

    /**
     * Cancels a pending approval for the given target (e.g. if the target disconnects).
     */
    public static void cancelApproval(UUID targetUuid) {
        PENDING.remove(targetUuid);
    }

    public static boolean hasPendingApproval(UUID targetUuid) {
        return PENDING.containsKey(targetUuid);
    }

    // ── Tick ──────────────────────────────────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) return;

        for (UUID targetUuid : new java.util.HashSet<>(PENDING.keySet())) {
            Approval approval = PENDING.get(targetUuid);
            if (approval == null) continue;

            ServerPlayerEntity target = server.getPlayerManager().getPlayer(targetUuid);
            if (target == null) {
                // Target logged off — cancel silently
                PENDING.remove(targetUuid);
                continue;
            }

            ServerPlayerEntity supervisor =
                server.getPlayerManager().getPlayer(approval.supervisorUuid());
            if (supervisor == null) {
                // Supervisor logged off — cancel and notify target
                PENDING.remove(targetUuid);
                target.sendMessage(Text.translatable("tycoon.supervisor.cancelled"), false);
                continue;
            }

            // Decrement
            Approval updated = approval.decrement();

            if (updated.remainingTicks() <= 0) {
                PENDING.remove(targetUuid);
                complete(supervisor, target, server);
            } else {
                PENDING.put(targetUuid, updated);

                // Broadcast countdown every 20 ticks (once per second)
                if (updated.remainingTicks() % 20 == 0) {
                    int secondsLeft = updated.remainingTicks() / 20;
                    Text bar = Text.translatable("tycoon.supervisor.countdown", secondsLeft);
                    supervisor.sendMessage(bar, true);
                    target.sendMessage(bar, true);
                }
            }
        }
    }

    // ── Completion ────────────────────────────────────────────────────────────

    private static void complete(ServerPlayerEntity supervisor, ServerPlayerEntity target,
                                  MinecraftServer server) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);
        PlayerData data = manager.getOrCreate(target.getUuid());

        // Advance tier
        boolean advanced = TierManager.checkAndAdvanceTier(data);
        if (!advanced) {
            // Shouldn't happen, but guard anyway
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.already_max"), false);
            return;
        }

        data.pendingTierApproval = false;
        data.gymActive = false;
        GymSessionManager.recalculateIncome(data);
        manager.set(target.getUuid(), data);

        int newTier = data.tierLevel;

        // +100 fitness bonus
        FitnessManager.addPoints(target, 100);

        // Teleport both to the tier location (if configured)
        GymCoreConfig.TierLocation loc =
            GymCoreConfigLoader.get().tierLocations.get(newTier);
        if (loc != null) {
            target.requestTeleport(loc.x, loc.y, loc.z);
            target.setYaw(loc.yaw);
            supervisor.requestTeleport(loc.x, loc.y, loc.z);
            supervisor.setYaw(loc.yaw);
        }

        // Refresh the upgrade catalogue and bossbar for the new tier
        BossbarManager.update(target, data);
        NetworkHandler.sendPlayerData(target, data);
        NetworkHandler.sendUpgradeMeta(target);

        // Notify both players
        target.sendMessage(Text.translatable("pc.tier_up", newTier), false);
        supervisor.sendMessage(Text.translatable("tycoon.supervisor.approved",
            target.getName(), newTier), false);

        // Execute hardcoded tier bonuses (moneyhud + money grant)
        runTierBonusCommands(target, newTier, server);

        // Execute configured approval commands
        runApprovalCommands(supervisor, target, newTier, server);

        TycoonCore.LOGGER.info("[TycoonCore] Supervisor {} approved tier-up for {} → T{}.",
            supervisor.getName().getString(), target.getName().getString(), newTier);
    }

    // ── Tier bonus commands ───────────────────────────────────────────────────

    /**
     * Runs the fixed bonus commands that always fire when a player reaches a new tier:
     *   • /moneyhud tier <N>              — executed AS the player (client HUD update)
     *   • /scoreboard players add <name> money 10000  — money grant
     *
     * Called from both the supervisor approval path and /tycoon tier_up.
     */
    public static void runTierBonusCommands(ServerPlayerEntity player,
                                             int newTier, MinecraftServer server) {
        var cmdManager = server.getCommandManager();
        // Use player's command source with level 4 so /moneyhud targets the right player
        var playerSrc = player.getCommandSource().withLevel(4);
        var serverSrc = server.getCommandSource().withLevel(4);
        String name   = player.getName().getString();

        // /moneyhud tier <N>
        try {
            cmdManager.executeWithPrefix(playerSrc, "moneyhud tier " + newTier);
        } catch (Exception e) {
            TycoonCore.LOGGER.warn("[TycoonCore] moneyhud command failed for tier {}: {}", newTier, e.getMessage());
        }

        // /scoreboard players add <player> money 10000
        try {
            cmdManager.executeWithPrefix(serverSrc,
                "scoreboard players add " + name + " money 10000");
        } catch (Exception e) {
            TycoonCore.LOGGER.warn("[TycoonCore] Money grant failed for {}: {}", name, e.getMessage());
        }
    }

    // ── Command execution ─────────────────────────────────────────────────────

    private static void runApprovalCommands(ServerPlayerEntity supervisor,
                                             ServerPlayerEntity target,
                                             int newTier, MinecraftServer server) {
        List<String> commands =
            GymCoreConfigLoader.get().supervisorApprovalCommands.get(newTier);
        if (commands == null || commands.isEmpty()) return;

        var cmdManager = server.getCommandManager();
        var src = server.getCommandSource().withLevel(4);
        String playerName     = target.getName().getString();
        String supervisorName = supervisor.getName().getString();
        String tier           = String.valueOf(newTier);

        for (String cmd : commands) {
            try {
                String resolved = cmd
                    .replace("%player%",     playerName)
                    .replace("%supervisor%", supervisorName)
                    .replace("%tier%",       tier);
                cmdManager.executeWithPrefix(src, resolved);
            } catch (Exception e) {
                TycoonCore.LOGGER.warn("[TycoonCore] Supervisor command failed '{}': {}",
                    cmd, e.getMessage());
            }
        }
    }
}
