package me.rein.donutorder.commons.papi.placeholders;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.database.DatabaseManager;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class DonutOrderPlaceholder extends PlaceholderExpansion {

    private final Map<UUID, int[]> orderCache = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastUpdateTime = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 10000; // 10 seconds in milliseconds

    @Override
    public @NotNull String getIdentifier() {
        return "donutorder";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Rein";
    }

    @Override
    public @NotNull String getVersion() {
        return "1.0.0";
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        UUID playerUuid = player.getUniqueId();

        long currentTime = System.currentTimeMillis();
        Long lastUpdate = lastUpdateTime.get(playerUuid);

        if (lastUpdate == null || (currentTime - lastUpdate) > CACHE_DURATION) {
            refreshPlayerCache(playerUuid);
        }

        int[] stats = orderCache.get(playerUuid);
        if (stats == null) return "0";

        return switch (params.toLowerCase()) {
            case "totalorders" -> String.valueOf(stats[0]);
            case "ordersdelivered" -> String.valueOf(stats[1]);
            default -> "";
        };
    }

    private void refreshPlayerCache(UUID playerUuid) {
        List<Order> orders = DatabaseManager.getDatabase().getPlayerOrders(playerUuid);
        int totalOrders = orders.size();
        int completedOrders = 0;

        for (Order order : orders) {
            if (order.isComplete()) {
                completedOrders++;
            }
        }

        orderCache.put(playerUuid, new int[] {totalOrders, completedOrders});
        lastUpdateTime.put(playerUuid, System.currentTimeMillis());

        if (orderCache.size() > 100) {
            cleanupCache();
        }
    }

    private void cleanupCache() {
        long currentTime = System.currentTimeMillis();
        lastUpdateTime.entrySet().removeIf(entry ->
                (currentTime - entry.getValue()) > CACHE_DURATION * 3);

        Set<UUID> validPlayers = lastUpdateTime.keySet();
        orderCache.keySet().removeIf(uuid -> !validPlayers.contains(uuid));
    }

    /**
     * Call when a player's orders change
     */
    public void invalidateCache(UUID playerUuid) {
        orderCache.remove(playerUuid);
        lastUpdateTime.remove(playerUuid);
    }
}
