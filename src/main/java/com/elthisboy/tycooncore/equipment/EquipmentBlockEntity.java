package com.elthisboy.tycooncore.equipment;

import com.elthisboy.tycooncore.registry.ModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.util.math.BlockPos;

import java.util.UUID;

/**
 * Shared block entity for all gym equipment blocks.
 *
 * Only purpose: track which player (UUID) is currently using this machine
 * so that a second player gets a "machine in use" message instead of
 * being able to double-occupy the same block.
 *
 * State is not persisted across server restarts (exercise state is volatile).
 */
public class EquipmentBlockEntity extends BlockEntity {

    /** UUID of the player currently using this machine, or null if free. */
    public UUID currentUser = null;

    public EquipmentBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlocks.EQUIPMENT_BLOCK_ENTITY, pos, state);
    }

    // ── NBT (minimal — we don't persist the user UUID across restarts) ────────

    @Override
    protected void writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.writeNbt(nbt, registries);
        // Not persisting currentUser intentionally: exercise state resets on reload.
    }

    @Override
    protected void readNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registries) {
        super.readNbt(nbt, registries);
        currentUser = null; // always reset on load
    }

    // ── Sync to client (not needed for gameplay, but keeps things consistent) ─

    @Override
    public Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public NbtCompound toInitialChunkDataNbt(RegistryWrapper.WrapperLookup registries) {
        return createNbt(registries);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    public boolean isFree() {
        return currentUser == null;
    }

    public void occupy(UUID uuid) {
        currentUser = uuid;
        markDirty();
    }

    public void release() {
        currentUser = null;
        markDirty();
    }
}
