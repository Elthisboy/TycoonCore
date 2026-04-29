package com.elthisboy.tycooncore.equipment;

import com.elthisboy.tycooncore.TycoonCore;
import com.elthisboy.tycooncore.fitness.FitnessConfigLoader;
import com.elthisboy.tycooncore.fitness.FitnessManager;
import com.elthisboy.tycooncore.network.packet.ExerciseStatePayload;
import com.elthisboy.tycooncore.registry.ModBlocks;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.item.ItemStack;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.network.packet.s2c.play.EntityAnimationS2CPacket;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Core runtime controller for the gym equipment system.
 *
 * ── What it does every tick ──────────────────────────────────────────────────
 *   1. Detects sneak (exit trigger)
 *   2. Locks player position (server-side correction)
 *   3. Dispatches arm-swing animation packets to nearby players
 *   4. Plays looped sound effects
 *   5. Executes optional JSON-configured commands
 *
 * ── Animation design ─────────────────────────────────────────────────────────
 *   Animations use VANILLA ARM-SWING PACKETS (EntityAnimationS2CPacket).
 *   They are broadcast to all players within 48 blocks, making the exercising
 *   player VISUALLY RECOGNISABLE from distance, in third-person, and in
 *   spectator mode — exactly what a roleplay recording needs.
 *
 *   Machine patterns:
 *     DUMBBELL_BENCH  — alternating single-arm curls (L/R each half-loop)
 *     TREADMILL       — rapid alternating (×4 per loop) → running cadence
 *     BENCH_PRESS     — bilateral push (both arms simultaneously) × 2 per loop
 *     CABLE_MACHINE   — triple alternating pull (L/R/L per loop)
 *
 * ── Position locking ─────────────────────────────────────────────────────────
 *   Every tick the server checks whether the player has drifted more than 0.3
 *   blocks from their locked position.  If so it calls requestTeleport() to
 *   snap them back.  setVelocity(0,0,0) prevents momentum accumulation.
 *   Players CAN look around freely — only positional movement is blocked.
 */
public class ExerciseManager {

    // ── State ─────────────────────────────────────────────────────────────────

    private static final Map<UUID, ExerciseState> STATES = new HashMap<>();

    /** Broadcast range for arm-swing animation packets (in blocks). */
    private static final double ANIM_RANGE_SQ = 48.0 * 48.0;

    // ── Public API ────────────────────────────────────────────────────────────

    public static boolean isExercising(UUID uuid) {
        return STATES.containsKey(uuid);
    }

    public static ExerciseState getState(UUID uuid) {
        return STATES.get(uuid);
    }

    public static void startExercise(ServerPlayerEntity player, BlockPos blockPos,
                                      Direction facing, EquipmentType type) {
        ExerciseConfig cfg = ExerciseConfigLoader.get(type);

        double[] pos = lockedPosition(blockPos, facing, type);
        float    yaw = lockedYaw(facing, type);

        ExerciseState state = new ExerciseState(
            player.getUuid(), type, blockPos,
            pos[0], pos[1], pos[2], yaw,
            cfg
        );
        STATES.put(player.getUuid(), state);

        // ── WEIGHT_LIFT: swap hands with weights items + trigger CPM animation ─
        if (type == EquipmentType.WEIGHT_LIFT) {
            state.savedMainSlot = player.getInventory().selectedSlot;
            state.savedMainHand = player.getInventory().getStack(state.savedMainSlot).copy();
            state.savedOffHand  = player.getOffHandStack().copy();

            ItemStack weights = new ItemStack(ModBlocks.WEIGHTS);
            player.getInventory().setStack(state.savedMainSlot, weights);
            player.getInventory().setStack(40, weights.copy()); // slot 40 = off hand

        }

        // ── BENCH_SIT: spawn invisible armor stand and force player to ride it ─
        if (type == EquipmentType.BENCH_SIT) {
            ServerWorld world = player.getServerWorld();
            // Position the stand so the player appears sitting at bench height.
            // Small armor stand mounted height offset ≈ 0.37 blocks.
            double seatX = blockPos.getX() + 0.5;
            double seatZ = blockPos.getZ() + 0.5;
            // Regular armor stand passenger offset ≈ 1.975 blocks.
            // Target: player appears at blockY + 0.5 (mid-block / bench seat level).
            // seatY = (blockY + 0.5) - 1.975 ≈ blockY - 1.475
            double seatY = blockPos.getY() - 1.475;

            ArmorStandEntity seat = new ArmorStandEntity(world, seatX, seatY, seatZ);
            seat.setInvisible(true);
            seat.setNoGravity(true);
            seat.setSilent(true);
            seat.setCustomNameVisible(false);
            world.spawnEntity(seat);

            player.requestTeleport(seatX, blockPos.getY(), seatZ);
            player.startRiding(seat, true);
            state.seatEntity = seat;
        }

        // ── Initial position snap (skip for WEIGHT_LIFT and BENCH_SIT) ──────────
        // TREADMILL_WALK DOES teleport — it uses standOnTop=true to get pos[] on top
        if (type != EquipmentType.WEIGHT_LIFT && type != EquipmentType.BENCH_SIT) {
            player.requestTeleport(pos[0], pos[1], pos[2]);
            player.setHeadYaw(yaw);
            player.setBodyYaw(yaw);
            player.setVelocity(0, 0, 0);
        }

        // Notify client (shows action-bar hint + tracks exercise state)
        ServerPlayNetworking.send(player, new ExerciseStatePayload(
            true, type.configId, (float) pos[0], (float) pos[1], (float) pos[2], yaw));


        // Commands — on_start
        if (cfg.commands.enabled) {
            runCommands(player, cfg.commands.onStart, cfg.commands.source);
        }

        TycoonCore.LOGGER.debug("[TycoonCore] {} started exercising on {}",
            player.getName().getString(), type.configId);
    }

    public static void stopExercise(ServerPlayerEntity player) {
        ExerciseState state = STATES.remove(player.getUuid());
        if (state == null) return;

        // Release block entity slot
        if (player.getServerWorld().getBlockEntity(state.blockPos)
                instanceof EquipmentBlockEntity ebe) {
            ebe.release();
        }

        // WEIGHT_LIFT: restore original hand items
        if (state.type == EquipmentType.WEIGHT_LIFT) {
            restoreHandItems(player, state);
        }

        // BENCH_SIT: dismount and kill the armor stand seat
        if (state.type == EquipmentType.BENCH_SIT && state.seatEntity != null) {
            player.stopRiding();
            state.seatEntity.discard();
            state.seatEntity = null;
        }

        // Notify client
        ServerPlayNetworking.send(player,
            new ExerciseStatePayload(false, "", 0, 0, 0, 0));

        // Show action-bar message
        player.sendMessage(Text.translatable("tycoon.equipment.stopped"), true);

        // Commands — on_stop
        if (state.config.commands.enabled) {
            runCommands(player, state.config.commands.onStop, state.config.commands.source);
        }

        TycoonCore.LOGGER.debug("[TycoonCore] {} stopped exercising.",
            player.getName().getString());
    }

    /** Called on player disconnect to clean up without sending network packets. */
    public static void forceStop(UUID uuid, ServerWorld world) {
        ExerciseState state = STATES.remove(uuid);
        if (state == null) return;

        BlockEntity be = world.getBlockEntity(state.blockPos);
        if (be instanceof EquipmentBlockEntity ebe) ebe.release();

        // WEIGHT_LIFT: restore items — player object is still accessible on disconnect
        if (state.type == EquipmentType.WEIGHT_LIFT) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(uuid);
            if (player != null) restoreHandItems(player, state);
        }

        // BENCH_SIT: discard the armor stand on disconnect
        if (state.type == EquipmentType.BENCH_SIT && state.seatEntity != null) {
            state.seatEntity.discard();
        }
    }

    private static void restoreHandItems(ServerPlayerEntity player, ExerciseState state) {
        if (state.savedMainHand != null) {
            player.getInventory().setStack(state.savedMainSlot, state.savedMainHand);
        }
        if (state.savedOffHand != null) {
            player.getInventory().setStack(40, state.savedOffHand);
        }
    }

    // ── Tick (registered in TycoonCore) ──────────────────────────────────────

    public static void tick(MinecraftServer server) {
        if (STATES.isEmpty()) return;

        for (UUID uuid : new java.util.HashSet<>(STATES.keySet())) {
            ExerciseState state = STATES.get(uuid);
            if (state == null) continue;

            ServerPlayerEntity player = server.getPlayerManager().getPlayer(uuid);
            if (player == null) {
                // Player logged off
                STATES.remove(uuid);
                continue;
            }

            // ── 1. Sneak = exit ───────────────────────────────────────────────
            if (player.isSneaking()) {
                stopExercise(player);
                continue;
            }

            // ── 1b. WEIGHT_LIFT: stop if weights removed from either hand ─────
            if (state.type == EquipmentType.WEIGHT_LIFT) {
                boolean mainHasWeights = player.getInventory()
                    .getStack(state.savedMainSlot).isOf(ModBlocks.WEIGHTS.asItem());
                boolean offHasWeights  = player.getOffHandStack()
                    .isOf(ModBlocks.WEIGHTS.asItem());
                if (!mainHasWeights || !offHasWeights) {
                    stopExercise(player);
                    continue;
                }
            }

            // ── 1c. BENCH_SIT: stop if player dismounted the seat ─────────────
            if (state.type == EquipmentType.BENCH_SIT) {
                boolean dismounted = state.seatEntity == null
                    || !state.seatEntity.isAlive()
                    || player.getVehicle() != state.seatEntity;
                if (dismounted) {
                    stopExercise(player);
                    continue;
                }
            }

            // ── 2. Position lock (skip for WEIGHT_LIFT and BENCH_SIT) ───────────
            if (state.config.lockPlayer
                    && state.type != EquipmentType.WEIGHT_LIFT
                    && state.type != EquipmentType.BENCH_SIT) {
                if (state.type == EquipmentType.TREADMILL_WALK) {
                    lockPositionHorizontal(player, state);
                } else {
                    lockPosition(player, state);
                }
            }

            // ── 3. Animation ──────────────────────────────────────────────────
            tickAnimation(player, state);

            // ── 4. Sound ──────────────────────────────────────────────────────
            if (state.config.sounds.enabled) {
                tickSound(player, state);
            }

            // ── 5. Commands (on_tick, once per second) ────────────────────────
            if (state.config.commands.enabled
                    && !state.config.commands.onTick.isEmpty()
                    && state.animationTick % 20 == 0) {
                runCommands(player, state.config.commands.onTick, state.config.commands.source);
            }

            // ── 6. Fitness score + action bar HUD ────────────────────────────
            FitnessManager.addPoints(player, FitnessConfigLoader.get().pointsPerTick);
            if (state.animationTick % 20 == 0) {
                FitnessManager.sendActionBarUpdate(player);
            }

            state.animationTick++;
        }
    }

    // ── Position locking ──────────────────────────────────────────────────────

    private static void lockPosition(ServerPlayerEntity player, ExerciseState state) {
        double dx = player.getX() - state.lockedX;
        double dy = player.getY() - state.lockedY;
        double dz = player.getZ() - state.lockedZ;

        // Zero velocity every tick to prevent drift
        player.setVelocity(0, 0, 0);

        // Teleport back if the player drifted more than 0.3 blocks
        if (dx * dx + dy * dy + dz * dz > 0.09) {
            player.requestTeleport(state.lockedX, state.lockedY, state.lockedZ);
        }

        // Keep body/head facing correct direction (allow free pitch look-around)
        player.setBodyYaw(state.lockedYaw);
    }

    /**
     * Treadmill-specific lock: freezes only horizontal (X/Z) movement.
     * Y is left to normal physics so the player stands naturally on the block
     * without the zero-velocity + gravity loop that causes bouncing.
     */
    private static void lockPositionHorizontal(ServerPlayerEntity player, ExerciseState state) {
        double dx = player.getX() - state.lockedX;
        double dz = player.getZ() - state.lockedZ;

        // Zero only horizontal velocity — preserve Y so gravity works normally
        player.setVelocity(0, player.getVelocity().y, 0);

        // Snap back horizontally if the player drifted more than 0.3 blocks
        if (dx * dx + dz * dz > 0.09) {
            player.requestTeleport(state.lockedX, player.getY(), state.lockedZ);
        }

        player.setBodyYaw(state.lockedYaw);
    }

    // ── Animation dispatch ────────────────────────────────────────────────────

    private static void tickAnimation(ServerPlayerEntity player, ExerciseState state) {
        int t    = state.animationTick % state.effectiveLoop();
        int loop = state.effectiveLoop();
        int half = loop / 2;
        int qtr  = Math.max(1, loop / 4);
        int third = Math.max(1, loop / 3);

        switch (state.type) {
            case DUMBBELL_BENCH -> {
                // Alternating dumbbell curls: L arm at 0, R arm at half-loop
                if (t == 0)    broadcastSwing(player, Hand.MAIN_HAND);
                if (t == half) broadcastSwing(player, Hand.OFF_HAND);
            }
            case TREADMILL -> {
                // Fast running cadence: 4 swings per loop, alternating
                if (t == 0)          broadcastSwing(player, Hand.MAIN_HAND);
                if (t == qtr)        broadcastSwing(player, Hand.OFF_HAND);
                if (t == qtr * 2)    broadcastSwing(player, Hand.MAIN_HAND);
                if (t == qtr * 3)    broadcastSwing(player, Hand.OFF_HAND);
            }
            case BENCH_PRESS -> {
                // Both arms push simultaneously — heavy, slow press
                if (t == 0 || t == half) {
                    broadcastSwing(player, Hand.MAIN_HAND);
                    broadcastSwing(player, Hand.OFF_HAND);
                }
            }
            case CABLE_MACHINE -> {
                // Alternating cable pulls — 3 pulls per loop
                if (t == 0)         broadcastSwing(player, Hand.MAIN_HAND);
                if (t == third)     broadcastSwing(player, Hand.OFF_HAND);
                if (t == third * 2) broadcastSwing(player, Hand.MAIN_HAND);
            }
            case DECO -> {
                // Simple alternating swing — generic for all decorative machines
                if (t == 0)    broadcastSwing(player, Hand.MAIN_HAND);
                if (t == half) broadcastSwing(player, Hand.OFF_HAND);
            }
            case WEIGHT_LIFT -> {
                // CPM handles the visual — no extra arm swings needed
            }
            case TREADMILL_WALK -> {
                // Same fast running cadence as the equipment treadmill
                if (t == 0)          broadcastSwing(player, Hand.MAIN_HAND);
                if (t == qtr)        broadcastSwing(player, Hand.OFF_HAND);
                if (t == qtr * 2)    broadcastSwing(player, Hand.MAIN_HAND);
                if (t == qtr * 3)    broadcastSwing(player, Hand.OFF_HAND);
            }
            case BENCH_SIT -> {
                // Slow alternating curls — seated bench exercise
                if (t == 0)    broadcastSwing(player, Hand.MAIN_HAND);
                if (t == half) broadcastSwing(player, Hand.OFF_HAND);
            }
        }
    }

    /**
     * Sends an arm-swing animation packet to all players within {@link #ANIM_RANGE_SQ}.
     *
     * {@code EntityAnimationS2CPacket} is the standard Minecraft packet for arm
     * swing animations. It makes the player model's arm visibly swing on every
     * nearby client — readable from a distance, in third-person, and in spectator.
     */
    private static void broadcastSwing(ServerPlayerEntity player, Hand hand) {
        if (!(player.getWorld() instanceof ServerWorld world)) return;

        int animId = (hand == Hand.MAIN_HAND)
            ? EntityAnimationS2CPacket.SWING_MAIN_HAND
            : EntityAnimationS2CPacket.SWING_OFF_HAND;

        EntityAnimationS2CPacket pkt = new EntityAnimationS2CPacket(player, animId);

        for (ServerPlayerEntity nearby : world.getPlayers()) {
            if (nearby.squaredDistanceTo(player) <= ANIM_RANGE_SQ) {
                nearby.networkHandler.sendPacket(pkt);
            }
        }
    }

    // ── Sound ─────────────────────────────────────────────────────────────────

    private static void tickSound(ServerPlayerEntity player, ExerciseState state) {
        ExerciseConfig.SoundConfig sc = state.config.sounds;
        if (state.animationTick % Math.max(1, sc.intervalTicks) != 0) return;

        SoundEvent sound = Registries.SOUND_EVENT.get(Identifier.tryParse(sc.soundId));
        if (sound == null) {
            TycoonCore.LOGGER.warn("[TycoonCore] Unknown sound: {}", sc.soundId);
            return;
        }

        // null player arg = ALL nearby players hear it (not just the exercising one)
        player.getServerWorld().playSound(
            null,
            player.getX(), player.getY(), player.getZ(),
            sound,
            SoundCategory.PLAYERS,
            sc.volume,
            sc.pitch + (float)(Math.random() * 0.1 - 0.05)  // slight pitch variation
        );
    }

    // ── Command execution ─────────────────────────────────────────────────────

    private static void runCommands(ServerPlayerEntity player,
                                     java.util.List<String> cmds,
                                     String source) {
        if (cmds == null || cmds.isEmpty()) return;
        MinecraftServer server = player.getServer();
        if (server == null) return;

        var cmdManager = server.getCommandManager();
        var src = source.equalsIgnoreCase("server")
            ? server.getCommandSource().withLevel(4)
            : player.getCommandSource();

        for (String cmd : cmds) {
            try {
                // Replace %player% placeholder with the player's name
                String resolved = cmd.replace("%player%", player.getName().getString());
                cmdManager.executeWithPrefix(src, resolved);
            } catch (Exception e) {
                TycoonCore.LOGGER.warn("[TycoonCore] Command failed '{}': {}", cmd, e.getMessage());
            }
        }
    }

    // ── Position helpers ──────────────────────────────────────────────────────

    /**
     * Calculates the world position the player snaps to when they start using a machine.
     *
     * Non-treadmill: player stands 1.5 blocks IN FRONT of the block on its facing side.
     * Treadmill:     player stands ON TOP of the block, centred.
     */
    static double[] lockedPosition(BlockPos blockPos, Direction facing, EquipmentType type) {
        double bx = blockPos.getX() + 0.5;
        double bz = blockPos.getZ() + 0.5;

        if (type.standOnTop) {
            // Treadmill: on top, centred
            return new double[]{ bx, blockPos.getY() + 1.0, bz };
        } else {
            // Stand in front (on the machine's facing side)
            return new double[]{
                bx + facing.getOffsetX() * 1.5,
                blockPos.getY(),
                bz + facing.getOffsetZ() * 1.5
            };
        }
    }

    /**
     * Calculates the yaw the player faces when locked into this machine.
     *
     * Non-treadmill: player faces TOWARD the machine (opposite of FACING direction).
     * Treadmill:     player faces SAME direction as FACING (running forward).
     *
     * Minecraft yaw: 0°=south, 90°=west, 180°=north, -90°=east
     */
    static float lockedYaw(Direction facing, EquipmentType type) {
        Direction lookDir = type.standOnTop ? facing : facing.getOpposite();
        return switch (lookDir) {
            case SOUTH -> 0f;
            case WEST  -> 90f;
            case NORTH -> 180f;
            case EAST  -> -90f;
            default    -> 0f;
        };
    }
}
