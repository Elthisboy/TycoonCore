package com.elthisboy.tycooncore.registry;

import com.elthisboy.tycooncore.screen.PcScreenHandler;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.resource.featuretoggle.FeatureFlags;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.util.Identifier;

public class ModScreenHandlers {

    public static final ScreenHandlerType<PcScreenHandler> PC_SCREEN_HANDLER =
        Registry.register(
            Registries.SCREEN_HANDLER,
            Identifier.of("tycooncore", "pc_screen"),
            new ScreenHandlerType<>(PcScreenHandler::new, FeatureFlags.VANILLA_FEATURES)
        );

    /** Called during mod init to ensure the static block above runs. */
    public static void initialize() {}
}
