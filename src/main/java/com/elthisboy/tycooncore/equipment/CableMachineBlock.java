package com.elthisboy.tycooncore.equipment;

import com.mojang.serialization.MapCodec;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;

/**
 * Cable machine — player stands in front and performs alternating cable pulls.
 *
 * Visual shape: tall machine frame (full height, narrowed slightly on sides).
 */
public class CableMachineBlock extends EquipmentBlock {

    public static final MapCodec<CableMachineBlock> CODEC = createCodec(CableMachineBlock::new);

    private static final VoxelShape SHAPE = createCuboidShape(2, 0, 2, 14, 16, 14);

    public CableMachineBlock(Settings settings) {
        super(EquipmentType.CABLE_MACHINE, settings);
    }

    @Override
    public MapCodec<? extends CableMachineBlock> getCodec() {
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
