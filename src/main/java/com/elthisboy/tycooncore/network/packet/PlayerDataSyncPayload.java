package com.elthisboy.tycooncore.network.packet;

import com.elthisboy.tycooncore.data.PlayerData;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Server → Client: live player-data snapshot.
 * Sent after every upgrade purchase, income tick, and on join.
 *
 * Includes incomePerSecond and gymActive so the PC GUI always shows
 * the live income rate and gym state without extra packets.
 */
public record PlayerDataSyncPayload(
    int                  tierLevel,
    int                  upgradeProgress,
    long                 money,
    Map<String, Integer> upgradeLevels,
    int                  incomePerSecond,
    boolean              gymActive
) implements CustomPayload {

    public static final CustomPayload.Id<PlayerDataSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "player_data_sync"));

    /** Convenience constructor: build from server-side data + scoreboard balance. */
    public PlayerDataSyncPayload(PlayerData data, long money) {
        this(data.tierLevel,
             data.upgradeProgress,
             money,
             new HashMap<>(data.upgradeLevels),
             data.incomePerSecond,
             data.gymActive);
    }

    public static final PacketCodec<RegistryByteBuf, PlayerDataSyncPayload> CODEC =
        new PacketCodec<>() {

            @Override
            public PlayerDataSyncPayload decode(RegistryByteBuf buf) {
                int  tier     = buf.readInt();
                int  progress = buf.readInt();
                long money    = buf.readLong();

                int mapSize = buf.readInt();
                Map<String, Integer> levels = new HashMap<>(mapSize);
                for (int i = 0; i < mapSize; i++) {
                    levels.put(buf.readString(), buf.readInt());
                }

                int     income    = buf.readInt();
                boolean gymActive = buf.readBoolean();

                return new PlayerDataSyncPayload(tier, progress, money, levels, income, gymActive);
            }

            @Override
            public void encode(RegistryByteBuf buf, PlayerDataSyncPayload p) {
                buf.writeInt(p.tierLevel());
                buf.writeInt(p.upgradeProgress());
                buf.writeLong(p.money());

                buf.writeInt(p.upgradeLevels().size());
                for (Map.Entry<String, Integer> e : p.upgradeLevels().entrySet()) {
                    buf.writeString(e.getKey());
                    buf.writeInt(e.getValue());
                }

                buf.writeInt(p.incomePerSecond());
                buf.writeBoolean(p.gymActive());
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
