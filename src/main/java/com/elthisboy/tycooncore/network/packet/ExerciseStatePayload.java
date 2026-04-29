package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server → Client: notifies the client that the local player started or stopped exercising.
 *
 * Fields:
 *   active   — true = started, false = stopped
 *   typeId   — equipment config ID (e.g. "dumbbell", "treadmill"); empty string when stopping
 *   x/y/z    — locked world position the server snapped the player to
 *   yaw      — locked body yaw the server applied
 *
 * The client uses this to:
 *   1. Show/hide the action-bar "Press Shift to exit" hint
 *   2. Track whether it is currently exercising (for future client-side effects)
 */
public record ExerciseStatePayload(
    boolean active,
    String  typeId,
    float   x,
    float   y,
    float   z,
    float   yaw
) implements CustomPayload {

    public static final CustomPayload.Id<ExerciseStatePayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "exercise_state"));

    public static final PacketCodec<RegistryByteBuf, ExerciseStatePayload> CODEC =
        new PacketCodec<>() {
            @Override
            public ExerciseStatePayload decode(RegistryByteBuf buf) {
                return new ExerciseStatePayload(
                    buf.readBoolean(),
                    buf.readString(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat(),
                    buf.readFloat()
                );
            }

            @Override
            public void encode(RegistryByteBuf buf, ExerciseStatePayload value) {
                buf.writeBoolean(value.active());
                buf.writeString(value.typeId());
                buf.writeFloat(value.x());
                buf.writeFloat(value.y());
                buf.writeFloat(value.z());
                buf.writeFloat(value.yaw());
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
