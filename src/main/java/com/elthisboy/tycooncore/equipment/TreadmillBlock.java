package com.elthisboy.tycooncore.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Treadmill — player stands ON TOP and runs in place.
 *
 * Full-height collision (so the player stands on top at blockY+1).
 * Visual shape is the same full cube — texture differentiation makes it clear.
 */
public class TreadmillBlock extends EquipmentBlock {

    public static final MapCodec<TreadmillBlock> CODEC = createCodec(TreadmillBlock::new);

    // Full block — player stands on top of it
    private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 16, 16);

    public TreadmillBlock(Settings settings) {
        super(EquipmentType.TREADMILL, settings);
    }

    @Override
    public MapCodec<? extends TreadmillBlock> getCodec() {
        return CODEC;
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world,
                                          BlockPos pos, ShapeContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world,
                                            BlockPos pos, ShapeContext context) {
        return SHAPE;
    }
}
