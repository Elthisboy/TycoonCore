package com.elthisboy.tycooncore.block;

import com.mojang.serialization.MapCodec;
import com.elthisboy.tycooncore.registry.ModScreenHandlers;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

/**
 * The PC block – right-clicking it opens the central tycoon management GUI.
 * Faces toward the player who places it (like a furnace).
 */
public class PcBlock extends HorizontalFacingBlock {

    public static final MapCodec<PcBlock> CODEC = createCodec(PcBlock::new);

    private static final VoxelShape SHAPE =
        Block.createCuboidShape(2, 0, 2, 14, 16, 14);

    public PcBlock(Settings settings) {
        super(settings);
        setDefaultState(getStateManager().getDefaultState()
            .with(Properties.HORIZONTAL_FACING, Direction.NORTH));
    }

    @Override
    public MapCodec<? extends PcBlock> getCodec() {
        return CODEC;
    }

    // ── Block state ───────────────────────────────────────────────────────────

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        // Screen faces toward the placing player
        return getDefaultState().with(Properties.HORIZONTAL_FACING,
            ctx.getHorizontalPlayerFacing().getOpposite());
    }

    // ── Shape ─────────────────────────────────────────────────────────────────

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world,
                                      BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    // ── Interaction ───────────────────────────────────────────────────────────

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos,
                               PlayerEntity player, BlockHitResult hit) {
        if (!world.isClient()) {
            player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, inv, p) -> ModScreenHandlers.PC_SCREEN_HANDLER.create(syncId, inv),
                Text.translatable("screen.tycooncore.pc")
            ));
        }
        return ActionResult.SUCCESS;
    }
}
