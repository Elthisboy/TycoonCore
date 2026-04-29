package com.elthisboy.tycooncore.equipment;

import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Holds all per-player runtime state while they are using a piece of gym equipment.
 *
 * Instances live in {@link ExerciseManager#STATES}; they are created on
 * exercise-start and removed on exercise-stop or player disconnect.
 */
public class ExerciseState {

    // ── Identity ──────────────────────────────────────────────────────────────

    public final UUID          playerUuid;
    public final EquipmentType type;
    public final BlockPos      blockPos;

    // ── Locked world position ─────────────────────────────────────────────────

    public final double lockedX;
    public final double lockedY;
    public final double lockedZ;
    public final float  lockedYaw;

    // ── Runtime ───────────────────────────────────────────────────────────────

    /** Incremented every server tick; drives the animation and sound timers. */
    public int animationTick = 0;

    /** Loaded config for this machine type. */
    public final ExerciseConfig config;

    // ── BENCH_SIT — invisible armor stand vehicle (null for other types) ────────

    /** The invisible armor stand the player is riding while on the weight bench. */
    public Entity seatEntity = null;

    // ── WEIGHT_LIFT — saved hand items (null for other types) ─────────────────

    /** Copy of the item that was in the main hand before lifting started. */
    public ItemStack savedMainHand  = null;
    /** Hotbar slot index of the main hand when lifting started. */
    public int       savedMainSlot  = 0;
    /** Copy of the item that was in the off hand before lifting started. */
    public ItemStack savedOffHand   = null;

    // ── Constructor ───────────────────────────────────────────────────────────

    public ExerciseState(UUID playerUuid, EquipmentType type, BlockPos blockPos,
                         double lockedX, double lockedY, double lockedZ,
                         float lockedYaw, ExerciseConfig config) {
        this.playerUuid = playerUuid;
        this.type       = type;
        this.blockPos   = blockPos;
        this.lockedX    = lockedX;
        this.lockedY    = lockedY;
        this.lockedZ    = lockedZ;
        this.lockedYaw  = lockedYaw;
        this.config     = config;
    }

    /**
     * Returns the effective loop length in ticks, clamped to at least 1.
     * animationTick % effectiveLoop gives the phase within the cycle.
     */
    public int effectiveLoop() {
        int loop = (int) Math.max(1, config.loopDuration / Math.max(0.1, config.animationSpeed));
        return loop;
    }
}
