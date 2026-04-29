package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: delivers the current sabotage state for the receiving player.
 *
 * Sent:
 *   • when a sabotage starts
 *   • when the technician is called (technicianCallTimeMs updated)
 *   • when the sabotage is resolved or expires (active = false)
 *
 * The client uses wall-clock timestamps (startTimeMs, technicianCallTimeMs)
 * rather than remaining seconds so the displayed countdown stays accurate even
 * if the packet arrives with a tiny delay.
 */
public record SabotageStatePayload(
    boolean active,
    int     durationSeconds,
    long    penaltyAmount,
    long    startTimeMs,
    long    technicianCallTimeMs
) implements CustomPayload {

    public static final CustomPayload.Id<SabotageStatePayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "sabotage_state"));

    public static final PacketCodec<RegistryByteBuf, SabotageStatePayload> CODEC =
        new PacketCodec<>() {
            @Override
            public SabotageStatePayload decode(RegistryByteBuf buf) {
                return new SabotageStatePayload(
                    buf.readBoolean(),
                    buf.readInt(),
                    buf.readLong(),
                    buf.readLong(),
                    buf.readLong()
                );
            }

            @Override
            public void encode(RegistryByteBuf buf, SabotageStatePayload value) {
                buf.writeBoolean(value.active());
                buf.writeInt(value.durationSeconds());
                buf.writeLong(value.penaltyAmount());
                buf.writeLong(value.startTimeMs());
                buf.writeLong(value.technicianCallTimeMs());
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
