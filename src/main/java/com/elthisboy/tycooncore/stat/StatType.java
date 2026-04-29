package com.elthisboy.tycooncore.stat;

public enum StatType {
    SPEED,
    EFFICIENCY,
    PRODUCTION_RATE;

    public static final int MAX_LEVEL = 5;

    public String getDisplayName() {
        return switch (this) {
            case SPEED           -> "Speed";
            case EFFICIENCY      -> "Efficiency";
            case PRODUCTION_RATE -> "Production Rate";
        };
    }

    public String getDescription() {
        return switch (this) {
            case SPEED           -> "Increases movement speed";
            case EFFICIENCY      -> "Increases task efficiency";
            case PRODUCTION_RATE -> "Increases production speed";
        };
    }
}
