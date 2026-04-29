package com.elthisboy.tycooncore.bossbar;

import com.elthisboy.tycooncore.data.PlayerData;
import com.elthisboy.tycooncore.tier.TierManager;
import net.minecraft.entity.boss.BossBar;
import net.minecraft.entity.boss.ServerBossBar;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Manages one per-player {@link ServerBossBar} that reflects tier and progress.
 * Must be called after every upgrade purchase and on tier changes.
 *
 * Bossbar text uses a translation key so each client sees it in their own locale.
 */
public class BossbarManager {

    private static final Map<UUID, ServerBossBar> BARS = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates (if needed) and refreshes the bossbar for {@code player}.
     * Called after every upgrade and on join.
     */
    public static void update(ServerPlayerEntity player, PlayerData data) {
        UUID uuid = player.getUuid();

        ServerBossBar bar = BARS.computeIfAbsent(uuid, k -> {
            ServerBossBar b = new ServerBossBar(
                buildTitle(data),
                BossBar.Color.GREEN,
                BossBar.Style.NOTCHED_6
            );
            b.addPlayer(player);
            return b;
        });

        int required = TierManager.getRequiredUpgrades(data.tierLevel);
        float progress = (data.tierLevel >= TierManager.MAX_TIER)
            ? 1.0f
            : Math.min(1.0f, (float) data.upgradeProgress / required);

        bar.setName(buildTitle(data));
        bar.setPercent(Math.max(0.0f, progress));
        bar.setColor(tierColor(data.tierLevel));
    }

    /** Removes any stale bar then calls {@link #update} to create a fresh one. */
    public static void onPlayerJoin(ServerPlayerEntity player, PlayerData data) {
        remove(player.getUuid());
        update(player, data);
    }

    /** Removes the bossbar – call on player disconnect. */
    public static void remove(UUID uuid) {
        ServerBossBar bar = BARS.remove(uuid);
        if (bar != null) bar.clearPlayers();
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static Text buildTitle(PlayerData data) {
        if (data.tierLevel >= TierManager.MAX_TIER) {
            // Final tier: no progress fraction
            return Text.translatable("pc.bossbar.max", data.tierLevel);
        }
        int required = TierManager.getRequiredUpgrades(data.tierLevel);
        return Text.translatable("pc.bossbar", data.tierLevel, data.upgradeProgress, required);
    }

    private static BossBar.Color tierColor(int tier) {
        return switch (tier) {
            case 1  -> BossBar.Color.GREEN;
            case 2  -> BossBar.Color.YELLOW;
            case 3  -> BossBar.Color.RED;
            default -> BossBar.Color.WHITE;
        };
    }
}
