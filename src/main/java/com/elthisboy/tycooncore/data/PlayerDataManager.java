package com.elthisboy.tycooncore.data;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.registry.RegistryWrapper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.PersistentState;
import net.minecraft.world.PersistentStateManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager extends PersistentState {

    private static final String DATA_KEY = "tycooncore_player_data";

    private final Map<UUID, PlayerData> dataMap = new HashMap<>();

    public PlayerData getOrCreate(UUID uuid) {
        return dataMap.computeIfAbsent(uuid, k -> new PlayerData());
    }

    public void set(UUID uuid, PlayerData data) {
        dataMap.put(uuid, data);
        markDirty();
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        NbtCompound players = new NbtCompound();
        for (Map.Entry<UUID, PlayerData> entry : dataMap.entrySet()) {
            players.put(entry.getKey().toString(), entry.getValue().toNbt());
        }
        nbt.put("players", players);
        return nbt;
    }

    public static PlayerDataManager fromNbt(NbtCompound nbt, RegistryWrapper.WrapperLookup registryLookup) {
        PlayerDataManager manager = new PlayerDataManager();
        NbtCompound players = nbt.getCompound("players");
        for (String key : players.getKeys()) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerData data = PlayerData.fromNbt(players.getCompound(key));
                manager.dataMap.put(uuid, data);
            } catch (IllegalArgumentException ignored) {}
        }
        return manager;
    }

    private static final Type<PlayerDataManager> TYPE = new Type<>(
        PlayerDataManager::new,
        PlayerDataManager::fromNbt,
        null
    );

    public static PlayerDataManager getOrCreate(MinecraftServer server) {
        PersistentStateManager manager = server.getOverworld().getPersistentStateManager();
        return manager.getOrCreate(TYPE, DATA_KEY);
    }
}
