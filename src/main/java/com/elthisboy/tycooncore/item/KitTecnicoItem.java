package com.elthisboy.tycooncore.item;

import com.elthisboy.tycooncore.event.SabotageManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;

import java.util.List;

/**
 * Kit de Técnico para Reparaciones.
 *
 * Right-click a player with this item to repair their active sabotage.
 * One kit is consumed per successful repair.
 *
 * Intended for roleplay: a staff player (the "technician") physically arrives,
 * right-clicks the sabotaged player, and resolves the event.
 * Both parties receive chat feedback.
 */
public class KitTecnicoItem extends Item {

    public KitTecnicoItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tycooncore.technician_repair_kit.tooltip"));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user,
                                    LivingEntity entity, Hand hand) {
        // Only execute on the server
        if (user.getWorld().isClient()) return ActionResult.PASS;
        if (!(user   instanceof ServerPlayerEntity technician)) return ActionResult.PASS;
        if (!(entity instanceof ServerPlayerEntity target))     return ActionResult.PASS;

        // Prevent self-use
        if (technician.getUuid().equals(target.getUuid())) {
            technician.sendMessage(net.minecraft.text.Text.translatable(
                "tycoon.tech.self_repair"), false);
            return ActionResult.FAIL;
        }

        boolean fixed = SabotageManager.resolveByTechnician(technician, target);
        if (fixed) {
            stack.decrement(1);
            return ActionResult.SUCCESS;
        }
        return ActionResult.FAIL;
    }
}
