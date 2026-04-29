package com.elthisboy.tycooncore.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Client-side cache for the local player's exercise state.
 *
 * Updated by {@link com.elthisboy.tycooncore.client.network.ClientNetworkHandler}
 * when an {@link com.elthisboy.tycooncore.network.packet.ExerciseStatePayload} arrives.
 *
 * Shows a repeating action-bar hint ("Press Shift to exit") while exercising.
 * The hint is re-sent every second so it doesn't vanish between server ticks.
 */
public class ClientExerciseCache {

    public static boolean isExercising = false;
    public static String  typeId       = "";

    /** Tick counter driven by {@link com.elthisboy.tycooncore.client.TycoonCoreClient}. */
    private static int hintTick = 0;

    public static void update(boolean active, String type) {
        isExercising = active;
        typeId       = active ? type : "";
        hintTick     = 0;

        if (!active) {
            // Clear the action bar immediately on stop
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc.player != null) {
                mc.player.sendMessage(Text.literal(""), true);
            }
        }
    }

    /**
     * Called every client tick. Refreshes the action-bar hint once per second
     * so it doesn't fade between packets.
     */
    public static void tick() {
        if (!isExercising) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        hintTick++;
        if (hintTick % 20 == 0) {
            mc.player.sendMessage(
                Text.translatable("tycoon.equipment.hint"), true);
        }
    }
}
