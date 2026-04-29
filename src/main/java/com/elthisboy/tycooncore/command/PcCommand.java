package com.elthisboy.tycooncore.command;

import com.elthisboy.tycooncore.event.EventManager;
import com.elthisboy.tycooncore.event.SabotageManager;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType; // still used by recovery + mission
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.EntityArgumentType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import static net.minecraft.server.command.CommandManager.argument;
import static net.minecraft.server.command.CommandManager.literal;

/**
 * Registers the {@code /pc} command tree (requires permission level 2).
 *
 * <pre>
 * /pc event sabotage  <target> [duration] [penalty]
 * /pc event recovery  <target> <bonus>
 * /pc event mission   <target> <reward>  <progress>
 * </pre>
 *
 * Designed to be called by map scripts, command blocks, or admin staff to
 * inject tycoon-style events into a running game session.
 */
public class PcCommand {

    /** Registers all command nodes via Fabric's {@link CommandRegistrationCallback}. */
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
            dispatcher.register(

                literal("pc")
                    .requires(src -> src.hasPermissionLevel(2))

                    // ── /pc event ─────────────────────────────────────────────
                    .then(literal("event")

                        // ── sabotage ─────────────────────────────────────────
                        // Penalty is always 10% of the target's current money.
                        .then(literal("sabotage")
                            .then(argument("target", EntityArgumentType.player())

                                // with custom duration
                                .then(argument("duration", IntegerArgumentType.integer(1, 3600))
                                    .executes(ctx -> {
                                        ServerPlayerEntity target =
                                            EntityArgumentType.getPlayer(ctx, "target");
                                        int dur = IntegerArgumentType.getInteger(ctx, "duration");
                                        SabotageManager.startSabotage(target, dur);
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§c[TycoonCore] §7Sabotage started on §e" +
                                            target.getName().getString() +
                                            "§7 (duration=§e" + dur + "s§7, penalty=§c10%§7)."),
                                            true);
                                        return 1;
                                    })
                                )

                                // target only (default 60 s)
                                .executes(ctx -> {
                                    ServerPlayerEntity target =
                                        EntityArgumentType.getPlayer(ctx, "target");
                                    SabotageManager.startSabotage(target, 60);
                                    ctx.getSource().sendFeedback(() -> Text.literal(
                                        "§c[TycoonCore] §7Sabotage started on §e" +
                                        target.getName().getString() +
                                        "§7 (duration=§e60s§7, penalty=§c10%§7)."),
                                        true);
                                    return 1;
                                })
                            )
                        )

                        // ── recovery ─────────────────────────────────────────
                        .then(literal("recovery")
                            .then(argument("target", EntityArgumentType.player())
                                .then(argument("bonus", LongArgumentType.longArg(0))
                                    .executes(ctx -> {
                                        ServerPlayerEntity target =
                                            EntityArgumentType.getPlayer(ctx, "target");
                                        long bonus = LongArgumentType.getLong(ctx, "bonus");
                                        EventManager.applyRecovery(target, bonus);
                                        ctx.getSource().sendFeedback(() -> Text.literal(
                                            "§a[TycoonCore] §7Recovery §a+" + bonus +
                                            "§7 applied to §e" + target.getName().getString() + "§7."),
                                            true);
                                        return 1;
                                    })
                                )
                            )
                        )

                        // ── mission ───────────────────────────────────────────
                        .then(literal("mission")
                            .then(argument("target", EntityArgumentType.player())
                                .then(argument("reward", LongArgumentType.longArg(0))
                                    .then(argument("progress", IntegerArgumentType.integer(0))
                                        .executes(ctx -> {
                                            ServerPlayerEntity target =
                                                EntityArgumentType.getPlayer(ctx, "target");
                                            long reward   = LongArgumentType.getLong(ctx, "reward");
                                            int  progress = IntegerArgumentType.getInteger(ctx, "progress");
                                            EventManager.applySpecialMission(target, reward, progress);
                                            ctx.getSource().sendFeedback(() -> Text.literal(
                                                "§e[TycoonCore] §7Mission applied to §e" +
                                                target.getName().getString() +
                                                "§7 (reward=§e" + reward + "§7, progress=§a+" +
                                                progress + "§7)."),
                                                true);
                                            return 1;
                                        })
                                    )
                                )
                            )
                        )
                    )
            )
        );
    }
}
