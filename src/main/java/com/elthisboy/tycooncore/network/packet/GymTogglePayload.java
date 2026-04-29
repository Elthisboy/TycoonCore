package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: player clicked the gym toggle button in the PC footer.
 * No data needed — the server toggles data.gymActive for that player.
 */
public record GymTogglePayload() implements CustomPayload {

    public static final CustomPayload.Id<GymTogglePayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "gym_toggle"));

    public static final PacketCodec<RegistryByteBuf, GymTogglePayload> CODEC =
        new PacketCodec<>() {
            @Override
            public GymTogglePayload decode(RegistryByteBuf buf) {
                return new GymTogglePayload();
            }
            @Override
            public void encode(RegistryByteBuf buf, GymTogglePayload value) {
                // no data
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
