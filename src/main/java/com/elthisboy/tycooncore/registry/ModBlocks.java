package com.elthisboy.tycooncore.registry;

import com.elthisboy.tycooncore.block.PcBlock;
import com.elthisboy.tycooncore.deco.GymDecoBlock;
import com.elthisboy.tycooncore.equipment.BenchPressBlock;
import com.elthisboy.tycooncore.equipment.EquipmentType;
import com.elthisboy.tycooncore.equipment.CableMachineBlock;
import com.elthisboy.tycooncore.equipment.DumbbellBenchBlock;
import com.elthisboy.tycooncore.equipment.EquipmentBlockEntity;
import com.elthisboy.tycooncore.equipment.TreadmillBlock;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.object.builder.v1.block.entity.FabricBlockEntityTypeBuilder;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.Block;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.util.Identifier;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;

public class ModBlocks {

    public static final Block PC_BLOCK = register("pc_block",
        new PcBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool()));

    // ── Gym equipment blocks ──────────────────────────────────────────────────

    public static final Block DUMBBELL_BENCH = register("dumbbell_bench",
        new DumbbellBenchBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool()));

    public static final Block TREADMILL = register("treadmill",
        new TreadmillBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool()));

    public static final Block BENCH_PRESS = register("bench_press",
        new BenchPressBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool()));

    public static final Block CABLE_MACHINE = register("cable_machine",
        new CableMachineBlock(AbstractBlock.Settings.create().strength(2.0f).requiresTool()));

    // ── Decorative gym blocks — hitbox shapes ─────────────────────────────────

    // Locker: tall cabinet (2 blocks high visually). Hitbox covers full block,
    // offset slightly forward (z starts at 1) matching the model's 1.5 unit gap.
    private static final VoxelShape LOCKER_SHAPE =
        Block.createCuboidShape( 0,  0,  1, 16, 16, 16);

    // Gym Bench: padded seat at ~y10 with two side frames and a cross bar.
    private static final VoxelShape GYM_BENCH_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 0,  0,  1,  2, 10, 15),   // left frame
        Block.createCuboidShape(14,  0,  1, 16, 10, 15),   // right frame
        Block.createCuboidShape( 2,  0,  7, 14,  1,  9),   // bottom cross bar
        Block.createCuboidShape( 0, 10,  0, 16, 11, 16)    // seat pad
    );

    // Weight Rack: horizontal rack frame with weights on both sides.
    // Wide flat body with a central upright column.
    private static final VoxelShape WEIGHT_RACK_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 1,  0,  4, 15,  2, 12),   // base sled
        Block.createCuboidShape( 6,  2,  5, 10, 14,  9),   // central upright post
        Block.createCuboidShape( 0,  8,  6, 16, 10,  8)    // horizontal barbell bar
    );

    // Weight Tree: tall central pole on a cross-shaped base, with weight
    // discs sticking out at multiple heights.
    private static final VoxelShape WEIGHT_TREE_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 0,  0,  6, 16,  1, 10),   // base cross (X axis)
        Block.createCuboidShape( 6,  0,  0, 10,  1, 16),   // base cross (Z axis)
        Block.createCuboidShape( 7,  0,  7,  9, 16,  9),   // central vertical pole
        Block.createCuboidShape( 5,  4,  1, 11, 10,  3),   // front weight cluster
        Block.createCuboidShape( 5,  4, 13, 11, 10, 15),   // back weight cluster
        Block.createCuboidShape( 5, 10,  2, 11, 14,  4),   // front upper weights
        Block.createCuboidShape( 5, 10, 12, 11, 14, 14)    // back upper weights
    );

    // Cross Trainer (elliptical): tall compact machine with a solid body,
    // pedal arms and handles rising up.
    private static final VoxelShape CROSS_TRAINER_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 2,  0,  1, 14,  4, 15),   // base platform
        Block.createCuboidShape( 4,  4,  3, 12, 14, 10),   // main body / flywheel
        Block.createCuboidShape( 5, 12,  2, 11, 16,  6)    // upper handles
    );

    // Power Rack: flat 1-voxel footprint covering the rack base.
    private static final VoxelShape POWER_RACK_SHAPE =
        Block.createCuboidShape( 0,  0,  4, 16,  1, 12);

    // Gym Treadmill: flat 1-voxel footprint covering the belt area.
    private static final VoxelShape GYM_TREADMILL_SHAPE =
        Block.createCuboidShape( 0,  0,  0, 16,  1, 16);

    // Weights: two circular weight plates on the floor with a connecting bar.
    // Hitbox mirrors the model elements exactly (all within 0-16).
    private static final VoxelShape WEIGHTS_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 1,  0,  5,  5,  5, 11),   // left plate
        Block.createCuboidShape( 5,  1,  7, 11,  3,  9),   // connecting bar
        Block.createCuboidShape(11,  0,  5, 15,  5, 11)    // right plate
    );

    // Weight Bench: inclined padded bench with upright post and barbell J-hooks.
    private static final VoxelShape WEIGHT_BENCH_SHAPE = VoxelShapes.union(
        Block.createCuboidShape( 3,  0,  0, 13,  2, 16),   // floor base
        Block.createCuboidShape( 4,  2,  0, 12,  7,  4),   // front leg support
        Block.createCuboidShape( 3,  5,  2, 13, 10, 16),   // bench pad
        Block.createCuboidShape( 2,  7, 13, 14, 16, 16),   // upright post
        Block.createCuboidShape( 0, 14, 13, 16, 16, 16)    // barbell bar
    );

    // ── Block registrations ───────────────────────────────────────────────────

    private static final AbstractBlock.Settings DECO_SETTINGS =
        AbstractBlock.Settings.create().strength(2.0f).requiresTool().nonOpaque();

    public static final Block LOCKER    = register("locker",
        new GymDecoBlock(DECO_SETTINGS, LOCKER_SHAPE));

    public static final Block GYM_BENCH = register("gym_bench",
        new GymDecoBlock(DECO_SETTINGS, GYM_BENCH_SHAPE));

    public static final Block WEIGHT_RACK  = register("weight_rack",
        new GymDecoBlock(DECO_SETTINGS, WEIGHT_RACK_SHAPE, EquipmentType.WEIGHT_LIFT));

    public static final Block WEIGHT_TREE  = register("weight_tree",
        new GymDecoBlock(DECO_SETTINGS, WEIGHT_TREE_SHAPE, EquipmentType.WEIGHT_LIFT));

    public static final Block CROSS_TRAINER = register("cross_trainer",
        new GymDecoBlock(DECO_SETTINGS, CROSS_TRAINER_SHAPE));

    public static final Block POWER_RACK   = register("power_rack",
        new GymDecoBlock(DECO_SETTINGS, POWER_RACK_SHAPE));

    public static final Block GYM_TREADMILL = register("gym_treadmill",
        new GymDecoBlock(DECO_SETTINGS, GYM_TREADMILL_SHAPE, EquipmentType.TREADMILL_WALK));

    public static final Block WEIGHT_BENCH = register("weight_bench",
        new GymDecoBlock(DECO_SETTINGS, WEIGHT_BENCH_SHAPE, EquipmentType.BENCH_SIT));

    public static final Block WEIGHTS = register("weights",
        new GymDecoBlock(DECO_SETTINGS, WEIGHTS_SHAPE));

    public static final Block WEIGHTS_DIRT = register("weights_dirt",
        new GymDecoBlock(DECO_SETTINGS, WEIGHTS_SHAPE));

    // ── Shared block entity type for all equipment blocks ─────────────────────
    // Declared AFTER the 4 block fields so they are initialised first.

    public static final BlockEntityType<EquipmentBlockEntity> EQUIPMENT_BLOCK_ENTITY =
        Registry.register(
            Registries.BLOCK_ENTITY_TYPE,
            Identifier.of("tycooncore", "equipment"),
            FabricBlockEntityTypeBuilder.create(EquipmentBlockEntity::new,
                DUMBBELL_BENCH, TREADMILL, BENCH_PRESS, CABLE_MACHINE
            ).build()
        );

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Block register(String name, Block block) {
        Identifier id = Identifier.of("tycooncore", name);
        Registry.register(Registries.BLOCK, id, block);
        Registry.register(Registries.ITEM, id, new BlockItem(block, new Item.Settings()));
        return block;
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.FUNCTIONAL).register(entries -> {
            entries.add(PC_BLOCK);
            entries.add(DUMBBELL_BENCH);
            entries.add(TREADMILL);
            entries.add(BENCH_PRESS);
            entries.add(CABLE_MACHINE);
            entries.add(LOCKER);
            entries.add(GYM_BENCH);
            entries.add(WEIGHT_RACK);
            entries.add(WEIGHT_TREE);
            entries.add(CROSS_TRAINER);
            entries.add(POWER_RACK);
            entries.add(GYM_TREADMILL);
            entries.add(WEIGHT_BENCH);
            entries.add(WEIGHTS);
            entries.add(WEIGHTS_DIRT);
        });
    }
}
