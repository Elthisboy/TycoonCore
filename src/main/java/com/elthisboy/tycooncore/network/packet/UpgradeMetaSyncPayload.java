package com.elthisboy.tycooncore.network.packet;

import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server → Client: full upgrade catalogue + UI metadata.
 * Sent once on player join (and after a live reload).
 *
 * Carries:
 *   currencySymbol  – shown in GUI cost labels
 *   entries         – one {@link UpgradeClientEntry} per registered upgrade,
 *                     including the income_bonus field for live display
 */
public record UpgradeMetaSyncPayload(
    String currencySymbol,
    List<UpgradeClientEntry> entries
) implements CustomPayload {

    public static final CustomPayload.Id<UpgradeMetaSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("tycooncore", "upgrade_meta_sync"));

    public static final PacketCodec<RegistryByteBuf, UpgradeMetaSyncPayload> CODEC =
        new PacketCodec<>() {

            @Override
            public UpgradeMetaSyncPayload decode(RegistryByteBuf buf) {
                String symbol = buf.readString();
                int count = buf.readInt();
                List<UpgradeClientEntry> list = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    list.add(new UpgradeClientEntry(
                        buf.readString(),   // id
                        buf.readString(),   // category
                        buf.readInt(),      // maxLevel
                        buf.readInt(),      // requiredTier
                        buf.readLong(),     // baseCost
                        buf.readFloat(),    // costMultiplier
                        buf.readBoolean(),  // countsForProgress
                        buf.readInt()       // incomeBonus
                    ));
                }
                return new UpgradeMetaSyncPayload(symbol, list);
            }

            @Override
            public void encode(RegistryByteBuf buf, UpgradeMetaSyncPayload p) {
                buf.writeString(p.currencySymbol());
                buf.writeInt(p.entries().size());
                for (UpgradeClientEntry e : p.entries()) {
                    buf.writeString(e.id());
                    buf.writeString(e.category());
                    buf.writeInt(e.maxLevel());
                    buf.writeInt(e.requiredTier());
                    buf.writeLong(e.baseCost());
                    buf.writeFloat(e.costMultiplier());
                    buf.writeBoolean(e.countsForProgress());
                    buf.writeInt(e.incomeBonus());
                }
            }
        };

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }
}
