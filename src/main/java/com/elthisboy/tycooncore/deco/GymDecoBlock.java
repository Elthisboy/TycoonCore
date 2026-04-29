package com.elthisboy.tycooncore.deco;

import com.elthisboy.tycooncore.equipment.EquipmentType;
import com.elthisboy.tycooncore.equipment.ExerciseManager;
import com.elthisboy.tycooncore.equipment.ExerciseState;
import com.mojang.serialization.MapCodec;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.HorizontalFacingBlock;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Properties;
import net.minecraft.util.ActionResult;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Generic decorative gym block — faces the placing player, no interactions.
 * The VoxelShape rotates automatically with the model for all 4 facing directions.
 */
public class GymDecoBlock extends HorizontalFacingBlock {

    // Codec reconstructs with a full-cube shape; gameplay shape comes from the constructor arg.
    public static final MapCodec<GymDecoBlock> CODEC =
        createCodec(s -> new GymDecoBlock(s, VoxelShapes.fullCube()));

    /** One pre-rotated shape per horizontal facing direction. */
    private final Map<Direction, VoxelShape> shapes;

    /** Which exercise type starts when a player right-clicks this block. */
    private final EquipmentType exerciseType;

    /** Purely decorative — no right-click interaction. */
    public GymDecoBlock(Settings settings, VoxelShape northShape) {
        this(settings, northShape, null);
    }

    public GymDecoBlock(Settings settings, VoxelShape northShape, EquipmentType exerciseType) {
        super(settings);
        this.exerciseType = exerciseType;
        this.shapes = buildRotatedShapes(northShape);
        setDefaultState(getStateManager().getDefaultState()
            .with(FACING, Direction.NORTH));
    }

    // ── Shape rotation ────────────────────────────────────────────────────────

    /**
     * Pre-computes the shape rotated 0 / 90 / 180 / 270 degrees (CW from above)
     * so that getOutlineShape can simply look up the right one.
     */
    private static Map<Direction, VoxelShape> buildRotatedShapes(VoxelShape north) {
        Map<Direction, VoxelShape> map = new EnumMap<>(Direction.class);
        map.put(Direction.NORTH, north);
        map.put(Direction.EAST,  rotate(north, 1));
        map.put(Direction.SOUTH, rotate(north, 2));
        map.put(Direction.WEST,  rotate(north, 3));
        return map;
    }

    /**
     * Rotates a VoxelShape by {@code steps} × 90° clockwise around the block centre
     * (Y axis). Coordinates are in the 0-1 unit range that Box uses.
     *
     * One 90° CW step: x' = 1 - z,  z' = x   (Y unchanged)
     */
    private static VoxelShape rotate(VoxelShape shape, int steps) {
        List<VoxelShape> parts = new ArrayList<>();
        for (Box box : shape.getBoundingBoxes()) {
            double minX = box.minX, minY = box.minY, minZ = box.minZ;
            double maxX = box.maxX, maxY = box.maxY, maxZ = box.maxZ;
            for (int i = 0; i < steps; i++) {
                double nx = 1.0 - maxZ;
                double nz = minX;
                double nxMax = 1.0 - minZ;
                double nzMax = maxX;
                minX = nx;  maxX = nxMax;
                minZ = nz;  maxZ = nzMax;
            }
            parts.add(VoxelShapes.cuboid(minX, minY, minZ, maxX, maxY, maxZ));
        }
        return parts.stream().reduce(VoxelShapes.empty(), VoxelShapes::union);
    }

    // ── Block overrides ───────────────────────────────────────────────────────

    @Override
    public MapCodec<? extends GymDecoBlock> getCodec() { return CODEC; }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return getDefaultState().with(FACING,
            ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(Properties.HORIZONTAL_FACING);
    }

    @Override
    protected VoxelShape getOutlineShape(BlockState state, BlockView world,
                                         BlockPos pos, ShapeContext ctx) {
        return shapes.get(state.get(FACING));
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockView world,
                                            BlockPos pos, ShapeContext ctx) {
        return shapes.get(state.get(FACING));
    }

    // ── Interaction — generates fitness points like real equipment ────────────

    @Override
    protected ActionResult onUse(BlockState state, World world, BlockPos pos,
                                  PlayerEntity player, BlockHitResult hit) {
        // Null exerciseType = purely decorative block, no interaction
        if (exerciseType == null) return ActionResult.PASS;

        if (world.isClient()) return ActionResult.SUCCESS;

        ServerPlayerEntity serverPlayer = (ServerPlayerEntity) player;

        if (ExerciseManager.isExercising(serverPlayer.getUuid())) {
            ExerciseState es = ExerciseManager.getState(serverPlayer.getUuid());
            // Toggle off if clicking the same block
            if (pos.equals(es.blockPos)) {
                ExerciseManager.stopExercise(serverPlayer);
                return ActionResult.SUCCESS;
            }
            // Switch machines
            ExerciseManager.stopExercise(serverPlayer);
        }

        Direction facing = state.get(Properties.HORIZONTAL_FACING);
        ExerciseManager.startExercise(serverPlayer, pos, facing, exerciseType);
        return ActionResult.SUCCESS;
    }
}
