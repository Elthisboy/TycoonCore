package com.elthisboy.tycooncore.client;

/**
 * Thread-safe read-only cache of the player's current sabotage state on the client.
 *
 * Updated by {@link com.elthisboy.tycooncore.client.network.ClientNetworkHandler}
 * whenever a {@link com.elthisboy.tycooncore.network.packet.SabotageStatePayload} arrives.
 *
 * Technician resolution is roleplay-driven — there is no countdown timer for arrival.
 * The client only tracks whether a technician has been called or not.
 */
public final class ClientSabotageCache {

    /** True when a sabotage is active and unresolved for this player. */
    public static boolean active               = false;

    /** Total duration of the sabotage window in seconds. */
    public static int     durationSeconds      = 0;

    /** Money deducted on expiry, or half on pay-fix. */
    public static long    penaltyAmount        = 0L;

    /** Server-side wall-clock timestamp (epoch ms) when the sabotage started. */
    public static long    startTimeMs          = 0L;

    /**
     * Server-side wall-clock timestamp when the technician was called.
     * -1 while technician has not been called.
     */
    public static long    technicianCallTimeMs = -1L;

    // ── Mutator ───────────────────────────────────────────────────────────────

    public static void update(boolean active,
                              int     durationSeconds,
                              long    penaltyAmount,
                              long    startTimeMs,
                              long    technicianCallTimeMs) {
        ClientSabotageCache.active               = active;
        ClientSabotageCache.durationSeconds      = durationSeconds;
        ClientSabotageCache.penaltyAmount        = penaltyAmount;
        ClientSabotageCache.startTimeMs          = startTimeMs;
        ClientSabotageCache.technicianCallTimeMs = technicianCallTimeMs;
    }

    // ── Computed helpers (render-thread safe) ─────────────────────────────────

    /** Wall-clock seconds remaining on the sabotage timer. */
    public static int secondsRemaining() {
        if (!active) return 0;
        long elapsed = System.currentTimeMillis() - startTimeMs;
        return (int) Math.max(0L, durationSeconds - elapsed / 1000L);
    }

    /** True once the player has clicked "Llamar Técnico". */
    public static boolean isTechnicianCalled() {
        return technicianCallTimeMs >= 0;
    }

    private ClientSabotageCache() {}
}
