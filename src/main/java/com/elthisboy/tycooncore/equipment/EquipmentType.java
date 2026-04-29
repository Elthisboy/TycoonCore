package com.elthisboy.tycooncore.equipment;

/**
 * Identifies which piece of gym equipment a player is using.
 * The configId maps to the JSON filename under config/tycooncore/gym_equipment/.
 */
public enum EquipmentType {

    DUMBBELL_BENCH ("dumbbell",    false),   // stand in front, alternating curls
    TREADMILL      ("treadmill",   true),    // stand on top, running motion
    BENCH_PRESS    ("bench_press", false),   // stand in front, bilateral push
    CABLE_MACHINE  ("cable",       false),   // stand in front, alternating pull
    DECO           ("deco",          false),  // decorative machines — no position lock
    WEIGHT_LIFT    ("weight_lift",   false),  // weight rack/tree — replaces hands with weights
    TREADMILL_WALK ("treadmill_walk",true),   // deco treadmill — teleports on top, player walks freely
    BENCH_SIT      ("bench_sit",     false);  // weight bench — player sits via invisible armor stand

    /** Filename stem for JSON config and translation keys. */
    public final String configId;

    /**
     * If true the player is locked on TOP of the block (treadmill).
     * If false the player is locked IN FRONT of the block.
     */
    public final boolean standOnTop;

    EquipmentType(String configId, boolean standOnTop) {
        this.configId    = configId;
        this.standOnTop  = standOnTop;
    }
}
