package com.elthisboy.tycooncore.gym;

import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.network.NetworkHandler;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

/**
 * Enforces the day/night gym schedule:
 *
 *   • At nightfall (time ≥ 13 000 ticks) every online player's gym is closed
 *     automatically and a warning is broadcast.
 *   • At dawn (time crosses back below 13 000) players are reminded they can
 *     reopen.
 *   • isNight() is used by TycoonCommand and NetworkHandler to block opening
 *     during the night, so players are forced to wait until morning.
 *   • On player login during night, any gym that somehow stayed active is
 *     closed immediately (see TycoonCore join handler).
 *
 * Night threshold: tick 13 000 of the 24 000-tick Minecraft day.
 * This matches the moment the sky becomes visually dark (~sunset transition).
 */
public class NightGymManager {

    /** Ticks into the day when night begins (sun fully set, sky dark). */
    private static final long NIGHT_START = 13_000L;

    /** Tracks previous tick's night state to detect the exact transition moment. */
    private static boolean wasNight = false;

    // ── Tick handler ──────────────────────────────────────────────────────────

    /**
     * Must be registered in TycoonCore via {@code ServerTickEvents.END_SERVER_TICK}.
     * Fires every server tick; detects day↔night transitions.
     */
    public static void tick(MinecraftServer server) {
        boolean night = isNight(server);

        if (night && !wasNight) {
            // ── Transition: day → night ───────────────────────────────────────
            closeAllGyms(server);
        } else if (!night && wasNight) {
            // ── Transition: night → day ───────────────────────────────────────
            notifyDawn(server);
        }

        wasNight = night;
    }

    // ── Public helpers ────────────────────────────────────────────────────────

    /**
     * Returns true when the overworld time is within the night window.
     * Safe to call from any server thread.
     */
    public static boolean isNight(MinecraftServer server) {
        long time = server.getOverworld().getTimeOfDay() % 24_000L;
        return time >= NIGHT_START;
    }

    /**
     * Closes the gym for a single player if it is currently open, and syncs
     * the new state to the client.  Used on player login during the night.
     *
     * @return true if the gym was actually closed
     */
    public static boolean closeIfNight(MinecraftServer server, ServerPlayerEntity player) {
        if (!isNight(server)) return false;

        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);
        PlayerData data = manager.getOrCreate(player.getUuid());

        if (!data.gymActive) return false;

        data.gymActive = false;
        manager.set(player.getUuid(), data);
        NetworkHandler.sendPlayerData(player, data);

        player.sendMessage(Text.translatable("tycoon.night.login_closed"), false);

        return true;
    }

    // ── Internals ─────────────────────────────────────────────────────────────

    /** Closes every online player's gym and notifies them. */
    private static void closeAllGyms(MinecraftServer server) {
        PlayerDataManager manager = PlayerDataManager.getOrCreate(server);

        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            PlayerData data = manager.getOrCreate(player.getUuid());

            if (!data.gymActive) continue;

            data.gymActive = false;
            manager.set(player.getUuid(), data);
            NetworkHandler.sendPlayerData(player, data);

            player.sendMessage(Text.translatable("tycoon.night.closed"), false);
        }
    }

    /** Notifies all online players that it is now daytime. */
    private static void notifyDawn(MinecraftServer server) {
        for (ServerPlayerEntity player : server.getPlayerManager().getPlayerList()) {
            player.sendMessage(Text.translatable("tycoon.night.dawn"), false);
        }
    }
}
