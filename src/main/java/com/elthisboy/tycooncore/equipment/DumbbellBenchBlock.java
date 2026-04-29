package com.elthisboy.tycooncore.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Dumbbell bench — player stands at the bench and performs alternating curls.
 *
 * Visual shape: low bench (8/16 tall), oriented toward facing direction.
 */
public class DumbbellBenchBlock extends EquipmentBlock {

    public static final MapCodec<DumbbellBenchBlock> CODEC = createCodec(DumbbellBenchBlock::new);

    private static final VoxelShape SHAPE = createCuboidShape(0, 0, 0, 16, 8, 16);

    public DumbbellBenchBlock(Settings settings) {
        super(EquipmentType.DUMBBELL_BENCH, settings);
    }

    @Override
    public MapCodec<? extends DumbbellBenchBlock> getCodec() {
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
