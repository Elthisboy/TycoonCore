package com.elthisboy.tycooncore.client;

import com.elthisboy.tycooncore.client.network.ClientNetworkHandler;
import com.elthisboy.tycooncore.client.screen.PcScreen;
import com.elthisboy.tycooncore.registry.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.gui.screen.ingame.HandledScreens;

public class TycoonCoreClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        // Bind the PC screen handler type to its GUI class
        HandledScreens.register(ModScreenHandlers.PC_SCREEN_HANDLER, PcScreen::new);

        // Register client-side packet receivers
        ClientNetworkHandler.register();

        // Refresh the "Press Shift to exit" action-bar hint once per second while exercising
        ClientTickEvents.END_CLIENT_TICK.register(client -> ClientExerciseCache.tick());
    }
}
