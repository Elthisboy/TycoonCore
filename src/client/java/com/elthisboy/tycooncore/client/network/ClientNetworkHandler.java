package com.elthisboy.tycooncore.client.network;

import com.elthisboy.tycooncore.client.ClientExerciseCache;
import com.elthisboy.tycooncore.client.ClientPlayerDataCache;
import com.elthisboy.tycooncore.client.ClientSabotageCache;
import com.elthisboy.tycooncore.client.ClientUpgradeCache;
import com.elthisboy.tycooncore.network.packet.ExerciseStatePayload;
import com.elthisboy.tycooncore.network.packet.PlayerDataSyncPayload;
import com.elthisboy.tycooncore.network.packet.SabotageStatePayload;
import com.elthisboy.tycooncore.network.packet.UpgradeMetaSyncPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;

/**
 * Registers client-side handlers for all server → client packets.
 *
 * All handlers run on the render thread (via {@code context.client().execute})
 * so cache updates are safe from concurrent access during rendering.
 */
public class ClientNetworkHandler {

    public static void register() {

        // ── PlayerDataSyncPayload ─────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
            PlayerDataSyncPayload.ID,
            (payload, context) -> context.client().execute(() ->
                ClientPlayerDataCache.update(
                    payload.tierLevel(),
                    payload.upgradeProgress(),
                    payload.money(),
                    payload.upgradeLevels(),
                    payload.incomePerSecond(),
                    payload.gymActive()
                )
            )
        );

        // ── UpgradeMetaSyncPayload ────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
            UpgradeMetaSyncPayload.ID,
            (payload, context) -> context.client().execute(() ->
                ClientUpgradeCache.update(payload)
            )
        );

        // ── SabotageStatePayload ──────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
            SabotageStatePayload.ID,
            (payload, context) -> context.client().execute(() ->
                ClientSabotageCache.update(
                    payload.active(),
                    payload.durationSeconds(),
                    payload.penaltyAmount(),
                    payload.startTimeMs(),
                    payload.technicianCallTimeMs()
                )
            )
        );

        // ── ExerciseStatePayload ──────────────────────────────────────────────
        ClientPlayNetworking.registerGlobalReceiver(
            ExerciseStatePayload.ID,
            (payload, context) -> context.client().execute(() ->
                ClientExerciseCache.update(payload.active(), payload.typeId())
            )
        );
    }
}
