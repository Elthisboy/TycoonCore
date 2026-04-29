package com.elthisboy.tycooncore.client;

import com.elthisboy.tycooncore.network.packet.UpgradeClientEntry;
import com.elthisboy.tycooncore.network.packet.UpgradeMetaSyncPayload;

import java.util.*;

/**
 * Client-side cache for the upgrade catalogue sent by the server in
 * {@link UpgradeMetaSyncPayload}.
 *
 * Populated once on join (and refreshed on live reload). The {@code PcScreen}
 * queries this instead of loading JSON files on the client, which means the
 * mod works correctly on dedicated servers where the client never sees the
 * server's config/gymcore/upgrades/ folder.
 */
public final class ClientUpgradeCache {

    // Insertion-ordered so tabs and rows appear in the same order the server loaded them
    private static final Map<String, UpgradeClientEntry> BY_ID  = new LinkedHashMap<>();
    private static final List<String>                    CATS    = new ArrayList<>();
    private static String currencySymbol = "$";

    private ClientUpgradeCache() {}

    // ── Update ────────────────────────────────────────────────────────────────

    public static void update(UpgradeMetaSyncPayload payload) {
        BY_ID.clear();
        CATS.clear();
        currencySymbol = payload.currencySymbol();

        Set<String> seenCats = new LinkedHashSet<>();
        for (UpgradeClientEntry e : payload.entries()) {
            BY_ID.put(e.id(), e);
            seenCats.add(e.category());
        }
        CATS.addAll(seenCats);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    public static List<String> getCategories() {
        return Collections.unmodifiableList(CATS);
    }

    public static List<UpgradeClientEntry> getByCategory(String category) {
        List<UpgradeClientEntry> result = new ArrayList<>();
        for (UpgradeClientEntry e : BY_ID.values()) {
            if (e.category().equalsIgnoreCase(category)) result.add(e);
        }
        return result;
    }

    public static UpgradeClientEntry get(String id) {
        return BY_ID.get(id);
    }

    public static boolean isEmpty() {
        return BY_ID.isEmpty();
    }

    public static String getCurrencySymbol() {
        return currencySymbol;
    }
}
