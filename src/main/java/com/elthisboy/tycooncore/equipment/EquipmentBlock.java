package com.elthisboy.tycooncore.equipment;

import net.minecraft.block.Block;
import net.minecraft.block.BlockEntityProvider;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import org.jetbrains.annotations.Nullable;

/**
 * Abstract base for all gym equipment blocks.
 *
 * Each subclass only needs to pass a {@link EquipmentType} to the constructor.
 * The entire interaction and animation logic is handled server-side by
 * {@link ExerciseManager}.
 *
 * Placement convention: the block faces TOWARD the player who places it
 * (like a furnace), so the player is always immediately in front of its
 * "active" side when they first interact.
 */
public abstract class EquipmentBlock extends HorizontalFacingBlock implements BlockEntityProvider {

    protected final EquipmentType equipmentType;

    protected EquipmentBlock(EquipmentType type, Settings settings) {
        super(settings);
        this.equipmentType = type;
        setDefaultState(getStateManager().getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    // ── Block state ───────────────────────────────────────────────────────────

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Face toward the placing player (active side = the side the player is on)
        return getDefaultState().with(Properties.HORIZONTAL_FACING,
            ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // ── Block entity ──────────────────────────────────────────────────────────

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new EquipmentBlockEntity(pos, state);
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                  PlayerEntity player, BlockHitResult hit) {
        if (world.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        // ── Player is already exercising on THIS machine → stop ───────────────
        if (ExerciseManager.isExercising(serverPlayer.getUuid())) {
            ExerciseState es = ExerciseManager.getState(serverPlayer.getUuid());
            if (pos.equals(es.blockPos)) {
                ExerciseManager.stopExercise(serverPlayer);
                return ActionResult.SUCCESS;
            }
            // Using a DIFFERENT machine while already on one — stop first, then start new
            ExerciseManager.stopExercise(serverPlayer);
        }

        // ── Check if machine is free ──────────────────────────────────────────
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof EquipmentBlockEntity ebe) {
            if (!ebe.isFree()) {
                serverPlayer.sendMessage(
                    Text.translatable("tycoon.equipment.in_use"), false);
                return ActionResult.FAIL;
            }
            ebe.occupy(serverPlayer.getUuid());
        }

        // ── Start exercising ──────────────────────────────────────────────────
        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        ExerciseManager.startExercise(serverPlayer, pos, facing, equipmentType);
        return ActionResult.SUCCESS;
    }

    // ── Break: release any player using this machine ──────────────────────────

    @Override
    public BlockState onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient()) {
            // If someone is using this machine, kick them out
            if (world.getBlockEntity(pos) instanceof EquipmentBlockEntity ebe
                    && ebe.currentUser != null) {
                ServerPlayerEntity user = world.getServer().getPlayerManager()
                    .getPlayer(ebe.currentUser);
                if (user != null) ExerciseManager.stopExercise(user);
            }
        }
        return super.onBreak(world, pos, state, player);
    }
}
