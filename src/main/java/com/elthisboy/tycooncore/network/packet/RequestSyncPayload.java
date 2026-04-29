package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: player opened/booted the PC and needs a fresh data snapshot.
 * Server responds with PlayerDataSyncPayload + UpgradeMetaSyncPayload.
 * No data payload needed — server reads the sender's UUID automatically.
 */
public record RequestSyncPayload() implements CustomPayload {

    public static final CustomPayload.Id<RequestSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "request_sync"));

    public static final PacketCodec<RegistryByteBuf, RequestSyncPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public RequestSyncPayload decode(RegistryByteBuf buf) {
                return new RequestSyncPayload();
            }
            @Override
            public void encode(RegistryByteBuf buf, RequestSyncPayload value) {
                // no data
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
