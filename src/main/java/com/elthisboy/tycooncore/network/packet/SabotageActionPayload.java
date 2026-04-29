package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Client → Server: player chose a sabotage resolution action from the PC GUI.
 *
 * Valid action strings (lowercase):
 *   "pay"         — pay half the penalty to fix immediately
 *   "item"        — consume one Emerald from inventory
 *   "technician"  — call the technician (15-second delayed auto-fix)
 */
public record SabotageActionPayload(String action) implements CustomPayload {

    public static final CustomPayload.Id<SabotageActionPayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "sabotage_action"));

    public static final PacketCodec<RegistryByteBuf, SabotageActionPayload> CODEC =
        new PacketCodec<>() {
            @Override
            public SabotageActionPayload decode(RegistryByteBuf buf) {
                return new SabotageActionPayload(buf.readString(32));
            }

            @Override
            public void encode(RegistryByteBuf buf, SabotageActionPayload value) {
                buf.writeString(value.action());
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
