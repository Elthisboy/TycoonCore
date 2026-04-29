package com.elthisboy.tycooncore.event;

import com.elthisboy.tycooncore.config.GymCoreConfigLoader;
import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.economy.ScoreboardEconomy;
import com.elthisboy.tycooncore.network.NetworkHandler;
import com.elthisboy.tycooncore.registry.ModBlocks;
import com.elthisboy.tycooncore.registry.ModItems;
import net.minecraft.block.Block;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Server-side controller for the sabotage event lifecycle.
 *
 * Resolutions:
 *   PAY        — player pays half the penalty directly
 *   ITEM       — player uses a Repair Kit from their inventory
 *   TECHNICIAN — a technician player right-clicks with Technician Repair Kit (roleplay)
 *
 * The state map is purely in-memory; sabotages do not persist across restarts.
 */
public class SabotageManager {

    private static final Map<UUID, SabotageState> STATES = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Activates a sabotage for the given player.
     * Penalty is always 10% of the player's current money (minimum 1).
     *
     * @param player          the targeted player
     * @param durationSeconds how long the player has to resolve before auto-penalty
     */
    public static void startSabotage(ServerPlayerEntity player, int durationSeconds) {
        long currentMoney = ScoreboardEconomy.getMoney(player);
        long penaltyAmount = Math.max(1L, currentMoney / 10);
        startSabotage(player, durationSeconds, penaltyAmount);
    }

    /** Internal overload that accepts an explicit penalty (used by the public API). */
    static void startSabotage(ServerPlayerEntity player,
                              int durationSeconds,
                              long penaltyAmount) {
        UUID uuid = player.getUuid();

        SabotageState state = new SabotageState();
        state.active               = true;
        state.startTimeMs          = System.currentTimeMillis();
        state.durationSeconds      = durationSeconds;
        state.penaltyAmount        = penaltyAmount;
        state.resolution           = SabotageState.Resolution.NONE;
        state.technicianCallTimeMs = -1L;

        STATES.put(uuid, state);

        String sym = GymCoreConfigLoader.get().currencySymbol;
        player.sendMessage(Text.translatable("tycoon.sabotage.started",
            durationSeconds, sym + penaltyAmount), false);

        NetworkHandler.sendSabotageState(player, state);

        // ── Visual + audio feedback ───────────────────────────────────────────
        spawnSabotageEffects(player);
    }

    /**
     * Finds the nearest PC_BLOCK within 20 blocks of the player, spawns smoke +
     * flame particles at it, and plays a bell-strike sound for dramatic effect.
     */
    private static void spawnSabotageEffects(ServerPlayerEntity player) {
        ServerWorld world = (ServerWorld) player.getWorld();
        BlockPos origin   = player.getBlockPos();
        int radius        = 20;

        BlockPos nearestPc = null;
        double   nearestDist = Double.MAX_VALUE;

        for (BlockPos pos : BlockPos.iterate(
                origin.add(-radius, -radius, -radius),
                origin.add( radius,  radius,  radius))) {
            if (world.getBlockState(pos).isOf(ModBlocks.PC_BLOCK)) {
                double d = pos.getSquaredDistance(origin);
                if (d < nearestDist) {
                    nearestDist = d;
                    nearestPc   = pos.toImmutable();
                }
            }
        }

        double px, py, pz;
        if (nearestPc != null) {
            px = nearestPc.getX() + 0.5;
            py = nearestPc.getY() + 1.0;
            pz = nearestPc.getZ() + 0.5;
        } else {
            // Fallback: above the player's head
            px = origin.getX() + 0.5;
            py = origin.getY() + 2.0;
            pz = origin.getZ() + 0.5;
        }

        // Spawn large smoke + flame burst
        world.spawnParticles(ParticleTypes.LARGE_SMOKE, px, py, pz, 30, 0.3, 0.4, 0.3, 0.05);
        world.spawnParticles(ParticleTypes.FLAME,       px, py, pz, 20, 0.2, 0.3, 0.2, 0.08);

        // Dramatic bell sound at the PC location
        world.playSound(null, px, py, pz,
            SoundEvents.BLOCK_BELL_USE, SoundCategory.BLOCKS, 1.5f, 0.5f);
    }

    /**
     * Pays half the penalty to immediately resolve the sabotage.
     */
    public static void resolvePay(ServerPlayerEntity player) {
        SabotageState state = STATES.get(player.getUuid());
        if (state == null || !state.active) return;

        String sym     = GymCoreConfigLoader.get().currencySymbol;
        long   fixCost = state.penaltyAmount / 2;
        long   balance = ScoreboardEconomy.getMoney(player);

        if (balance < fixCost) {
            player.sendMessage(Text.translatable("tycoon.sabotage.pay.no_money",
                sym + fixCost, sym + balance), false);
            return;
        }

        ScoreboardEconomy.setMoney(player, balance - fixCost);
        state.resolution = SabotageState.Resolution.PAY;
        resolveState(player, state,
            Text.translatable("tycoon.sabotage.pay.resolved", sym + fixCost));
    }

    /**
     * Consumes one Repair Kit from the player's inventory to resolve.
     */
    public static void resolveItem(ServerPlayerEntity player) {
        SabotageState state = STATES.get(player.getUuid());
        if (state == null || !state.active) return;

        // Search inventory for a Repair Kit
        boolean found = false;
        for (int i = 0; i < player.getInventory().size(); i++) {
            var stack = player.getInventory().getStack(i);
            if (!stack.isEmpty() && stack.isOf(ModItems.REPAIR_KIT)) {
                stack.decrement(1);
                found = true;
                break;
            }
        }

        if (!found) {
            player.sendMessage(Text.translatable("tycoon.sabotage.item.no_kit"), false);
            return;
        }

        state.resolution = SabotageState.Resolution.ITEM;
        resolveState(player, state, Text.translatable("tycoon.sabotage.item.resolved"));
    }

    /**
     * Marks the sabotage as "technician called".
     * No countdown — a real technician player must arrive and use KitTecnico.
     */
    public static void callTechnician(ServerPlayerEntity player) {
        SabotageState state = STATES.get(player.getUuid());
        if (state == null || !state.active) return;

        if (state.isTechnicianCalled()) {
            player.sendMessage(Text.translatable("tycoon.sabotage.tech.already_called"), false);
            return;
        }

        state.technicianCallTimeMs = System.currentTimeMillis();
        player.sendMessage(Text.translatable("tycoon.sabotage.tech.requested"), false);

        // Broadcast to ops so a staff member sees the request
        player.getServer().getPlayerManager().getPlayerList().forEach(p -> {
            if (p.hasPermissionLevel(2) && !p.getUuid().equals(player.getUuid())) {
                p.sendMessage(Text.translatable("tycoon.sabotage.tech.broadcast",
                    player.getName().getString()), false);
            }
        });

        NetworkHandler.sendSabotageState(player, state);
    }

    /**
     * Called by a technician player using KitTecnicoItem on the sabotaged player.
     * Resolves the sabotage as TECHNICIAN resolution.
     *
     * @param technician  the player performing the repair (must hold KitTecnico)
     * @param target      the sabotaged player
     * @return true if the sabotage was successfully resolved
     */
    public static boolean resolveByTechnician(ServerPlayerEntity technician,
                                              ServerPlayerEntity target) {
        SabotageState state = STATES.get(target.getUuid());

        if (state == null || !state.active) {
            technician.sendMessage(Text.translatable("tycoon.sabotage.tech.no_active",
                target.getName().getString()), false);
            return false;
        }

        state.resolution = SabotageState.Resolution.TECHNICIAN;

        // Message to the sabotaged player
        resolveState(target, state,
            Text.translatable("tycoon.sabotage.tech.resolved_victim",
                technician.getName().getString()));

        // Confirmation to the technician
        technician.sendMessage(Text.translatable("tycoon.sabotage.tech.confirmed",
            target.getName().getString()), false);

        return true;
    }

    /**
     * Server tick: handles only timer expiry.
     * Technician arrival is now roleplay-driven, not automatic.
     */
    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;

        for (Map.Entry<UUID, SabotageState> entry : new HashMap<>(STATES).entrySet()) {
            UUID          uuid  = entry.getKey();
            SabotageState state = entry.getValue();

            if (!state.active) {
                STATES.remove(uuid);
                continue;
            }

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) continue;

            // ── Timer expiry: apply penalty ───────────────────────────────────
            if (state.isExpired()) {
                String sym     = GymCoreConfigLoader.get().currencySymbol;
                long   current = ScoreboardEconomy.getMoney(player);
                long   after   = Math.max(0L, current - state.penaltyAmount);
                ScoreboardEconomy.setMoney(player, after);

                player.sendMessage(Text.translatable("tycoon.sabotage.expired",
                    sym + state.penaltyAmount), false);

                state.active = false;
                STATES.remove(uuid);
                NetworkHandler.sendSabotageState(player, state);
                syncEconomy(player);
            }
        }
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    /** Returns true if the player currently has an active, unresolved sabotage. */
    public static boolean isActive(UUID uuid) {
        SabotageState s = STATES.get(uuid);
        return s != null && s.active;
    }

    /** Returns the current state, or a blank inactive state if none exists. */
    public static SabotageState getState(UUID uuid) {
        return STATES.getOrDefault(uuid, new SabotageState());
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    private static void resolveState(ServerPlayerEntity player,
                                     SabotageState state,
                                     Text message) {
        state.active = false;
        STATES.remove(player.getUuid());

        player.sendMessage(message, false);
        NetworkHandler.sendSabotageState(player, state);
        syncEconomy(player);
    }

    private static void syncEconomy(ServerPlayerEntity player) {
        PlayerData data = PlayerDataManager
            .getOrCreate(player.getServer())
            .getOrCreate(player.getUuid());
        NetworkHandler.sendPlayerData(player, data);
    }
}
