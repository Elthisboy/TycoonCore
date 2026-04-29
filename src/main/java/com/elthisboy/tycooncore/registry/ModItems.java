package com.elthisboy.tycooncore.registry;

import com.elthisboy.tycooncore.item.KitTecnicoItem;
import com.elthisboy.tycooncore.item.SupervisorItem;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipType;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * Registers all custom TycoonCore items.
 *
 * Items:
 *   repair_kit             — used by the sabotaged player to self-repair (1 per fix)
 *   technician_repair_kit  — used by a technician to repair another player's sabotage
 */
public class ModItems {

    /** Repair Kit — sabotaged player uses this from their own inventory to fix. */
    public static final Item REPAIR_KIT = register("repair_kit",
        new Item(new Item.Settings().maxCount(16)) {
            @Override
            public void appendTooltip(ItemStack stack, TooltipContext context,
                                      List<Text> tooltip, TooltipType type) {
                tooltip.add(Text.translatable("item.tycooncore.repair_kit.tooltip"));
            }
        });

    /** Technician Repair Kit — right-click a sabotaged player to resolve their event. */
    public static final Item TECHNICIAN_REPAIR_KIT = register("technician_repair_kit",
        new KitTecnicoItem(new Item.Settings().maxCount(8)));

    /** Supervisor Clipboard — right-click a player to approve their tier-up. */
    public static final Item SUPERVISOR_CLIPBOARD = register("supervisor_clipboard",
        new SupervisorItem(new Item.Settings().maxCount(1)));

    // ── Registration ──────────────────────────────────────────────────────────

    private static Item register(String name, Item item) {
        return Registry.register(Registries.ITEM, Identifier.of("tycooncore", name), item);
    }

    public static void initialize() {
        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS)
            .register(entries -> {
                entries.add(REPAIR_KIT);
                entries.add(TECHNICIAN_REPAIR_KIT);
                entries.add(SUPERVISOR_CLIPBOARD);
            });
    }
}
