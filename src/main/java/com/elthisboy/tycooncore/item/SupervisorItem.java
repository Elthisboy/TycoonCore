package com.elthisboy.tycooncore.item;

import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.data.PlayerDataManager;
import com.elthisboy.tycooncore.supervisor.SupervisorManager;
import com.elthisboy.tycooncore.tier.TierManager;
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
 * Supervisor Clipboard — roleplay item held by the supervisor role.
 *
 * Right-click a player to:
 *   1. Check if they have pendingTierApproval (bought all required upgrades).
 *   2. If so, start a countdown after which the tier-up fires automatically.
 *   3. If not, show why they are not ready (progress / already max tier).
 *
 * The item is NOT consumed on use (it is a reusable roleplay tool).
 */
public class SupervisorItem extends Item {

    public SupervisorItem(Settings settings) {
        super(settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, TooltipContext context,
                               List<Text> tooltip, TooltipType type) {
        tooltip.add(Text.translatable("item.tycooncore.supervisor_clipboard.tooltip"));
    }

    @Override
    public ActionResult useOnEntity(ItemStack stack, PlayerEntity user,
                                    LivingEntity entity, Hand hand) {
        if (user.getWorld().isClient()) return ActionResult.PASS;
        if (!(user   instanceof ServerPlayerEntity supervisor)) return ActionResult.PASS;
        if (!(entity instanceof ServerPlayerEntity target))     return ActionResult.PASS;

        // Prevent self-use
        if (supervisor.getUuid().equals(target.getUuid())) {
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.self"), false);
            return ActionResult.FAIL;
        }

        PlayerData data = PlayerDataManager.getOrCreate(supervisor.getServer())
                                           .getOrCreate(target.getUuid());

        // Already at max tier
        if (data.tierLevel >= TierManager.MAX_TIER) {
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.already_max_tier",
                target.getName()), false);
            return ActionResult.FAIL;
        }

        // Has a countdown already running → cancel it (right-clicking again = cancel)
        if (SupervisorManager.hasPendingApproval(target.getUuid())) {
            SupervisorManager.cancelApproval(target.getUuid());
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.cancelled_by_supervisor",
                target.getName()), false);
            target.sendMessage(Text.translatable("tycoon.supervisor.cancelled"), false);
            return ActionResult.SUCCESS;
        }

        // Not yet ready — show current progress
        if (!data.pendingTierApproval) {
            int progress  = data.upgradeProgress;
            int required  = TierManager.getRequiredUpgrades(data.tierLevel);
            supervisor.sendMessage(Text.translatable("tycoon.supervisor.not_ready",
                target.getName(), progress, required), false);
            return ActionResult.FAIL;
        }

        // All checks passed — start the countdown
        SupervisorManager.startApproval(supervisor, target);
        return ActionResult.SUCCESS;
    }
}
