package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: player requests to purchase an upgrade by ID.
 */
public record UpgradeRequestPayload(String upgradeId) implements CustomPayload {

    public static final CustomPayload.Id<UpgradeRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "upgrade_request"));

    public static final PacketCodec<RegistryByteBuf, UpgradeRequestPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public UpgradeRequestPayload decode(RegistryByteBuf buf) {
                return new UpgradeRequestPayload(buf.readString());
            }

            @Override
            public void encode(RegistryByteBuf buf, UpgradeRequestPayload value) {
                buf.writeString(value.upgradeId());
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
