package com.elthisboy.tycooncore.event;

/**
 * Per-player sabotage state stored in {@link SabotageManager}.
 * Intentionally kept as a plain mutable data container — the manager
 * owns and mutates it; payloads carry a snapshot to the client.
 *
 * Technician resolution is now purely roleplay-driven:
 *   a technician player right-clicks with KitTecnico.
 *   There is no countdown — the sabotaged player waits for a real person to arrive.
 */
public class SabotageState {

    public enum Resolution { NONE, PAY, ITEM, TECHNICIAN }

    // ── Sabotage parameters ───────────────────────────────────────────────────
    public boolean    active               = false;
    public long       startTimeMs          = 0L;
    public int        durationSeconds      = 60;
    public long       penaltyAmount        = 0L;

    // ── Resolution tracking ───────────────────────────────────────────────────
    public Resolution resolution           = Resolution.NONE;

    /**
     * Wall-clock timestamp when the player called a technician.
     * -1 = technician not called.
     * Used only to show "technician called" status — there is no auto-arrival timer.
     */
    public long       technicianCallTimeMs = -1L;

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** True when the sabotage timer has elapsed without resolution. */
    public boolean isExpired() {
        return active && System.currentTimeMillis() >= startTimeMs + (durationSeconds * 1000L);
    }

    /** Wall-clock seconds until the sabotage timer runs out (0 when inactive or expired). */
    public int secondsRemaining() {
        if (!active) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return (int) Math.max(0L, durationSeconds - elapsed / 1000L);
    }

    /** True once the player has called a technician. */
    public boolean isTechnicianCalled() {
        return technicianCallTimeMs >= 0;
    }
}
