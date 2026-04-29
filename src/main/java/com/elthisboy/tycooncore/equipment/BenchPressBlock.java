package com.elthisboy.tycooncore.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Bench press — player stands at the bar end and performs bilateral arm pushes.
 *
 * Visual shape: bench height with uprights (simplified as full-width 10-tall block).
 */
public class BenchPressBlock extends EquipmentBlock {

    public static final MapCodec<BenchPressBlock> CODEC = createCodec(BenchPressBlock::new);

    private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 10, 16);

    public BenchPressBlock(Settings settings) {
        super(EquipmentType.BENCH_PRESS, settings);
    }

    @Override
    public MapCodec<? extends BenchPressBlock> getCodec() {
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
