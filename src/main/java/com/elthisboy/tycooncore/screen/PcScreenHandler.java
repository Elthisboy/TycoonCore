package com.elthisboy.tycooncore.screen;

import com.elthisboy.tycooncore.registry.ModScreenHandlers;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;

/**
 * Server-side screen handler for the PC GUI.
 * No inventory slots – all interaction happens via network packets.
 */
public class PcScreenHandler extends ScreenHandler {

    public PcScreenHandler(int syncId, PlayerInventory inventory) {
        super(ModScreenHandlers.PC_SCREEN_HANDLER, syncId);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return true;
    }

    /** No inventory slots – shift-clicking does nothing. */
    @Override
    public ItemStack quickMove(PlayerEntity player, int slot) {
        return ItemStack.EMPTY;
    }
}
