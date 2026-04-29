package com.elthisboy.tycooncore.upgrade;

import net.minecraft.text.Text;

/**
 * Result of an upgrade purchase attempt.
 * Uses {@link Text} so messages are fully translatable client-side.
 */
public record UpgradeResult(boolean success, Text message) {

    public static UpgradeResult success(Text message) {
        return new UpgradeResult(true, message);
    }

    public static UpgradeResult failure(Text message) {
        return new UpgradeResult(false, message);
    }

    /** Convenience – used internally where the message is never shown. */
    public static UpgradeResult ok() {
        return new UpgradeResult(true, Text.empty());
    }
}
