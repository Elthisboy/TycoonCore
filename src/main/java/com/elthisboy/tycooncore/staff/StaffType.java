package com.elthisboy.tycooncore.staff;

public enum StaffType {
    TRAINER,
    MANAGER,
    NUTRITIONIST,
    MARKETING;

    public static final int MAX_LEVEL = 5;

    public String getDisplayName() {
        return switch (this) {
            case TRAINER      -> "Trainer";
            case MANAGER      -> "Manager";
            case NUTRITIONIST -> "Nutritionist";
            case MARKETING    -> "Marketing";
        };
    }

    public String getDescription() {
        return switch (this) {
            case TRAINER      -> "Increases income per client";
            case MANAGER      -> "Increases passive income";
            case NUTRITIONIST -> "Reduces negative effects";
            case MARKETING    -> "Increases client flow";
        };
    }
}
