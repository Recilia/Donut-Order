package me.rein.donutorder.commons.manager;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.api.event.PlayerQueryItemEvent;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.manager.price.ItemPrice;
import me.rein.donutorder.commons.manager.price.PriceManager;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/*
 * I removed all the other comments that I added,
 * because they didn't make sense anymore to be kept there
 * as I'm sure you understand from this code without comms
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderManager {

    @Getter(lazy = true)
    private static final OrderManager instance = new OrderManager();

    private final Map<UUID, Order> orders = new ConcurrentHashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    private long lastVerificationTime = 0;
    private static final long VERIFICATION_THRESHOLD_MS = 15000;

    // Search indexes
    private final Multimap<String, Order> nameIndex = Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Multimap<String, Order> aliasIndex = Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Multimap<String, Order> loreIndex = Multimaps.synchronizedMultimap(HashMultimap.create());
    private final Multimap<Filter, Order> categoryIndex = Multimaps.synchronizedMultimap(HashMultimap.create());

    private final Map<Order, Set<String>> orderSearchTerms = new ConcurrentHashMap<>();

    private static class ItemIdentifier {
        private final Material material;
        private final String displayName;
        private final int customModelData;
        private final String nbtHash;

        public ItemIdentifier(ItemStack item) {
            this.material = item.getType();

            if (item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                this.displayName = meta.hasDisplayName()
                        ? ChatColor.stripColor(meta.getDisplayName())
                        : "";
                this.customModelData = meta.hasCustomModelData()
                        ? meta.getCustomModelData()
                        : 0;
                // For plugins like MMOItems, we'd need to check NBT
                this.nbtHash = generateNBTHash(item);
            } else {
                this.displayName = "";
                this.customModelData = 0;
                this.nbtHash = "";
            }
        }

        private String generateNBTHash(ItemStack item) {
            return String.valueOf(item.toString().hashCode());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ItemIdentifier that = (ItemIdentifier) o;
            return customModelData == that.customModelData &&
                    material == that.material &&
                    Objects.equals(displayName, that.displayName) &&
                    Objects.equals(nbtHash, that.nbtHash);
        }

        @Override
        public int hashCode() {
            return Objects.hash(material, displayName, customModelData, nbtHash);
        }
    }

    public Map<UUID, Order> getOrders() {
        if (!ValuesManager.getInstance().isCrossServerSupport()) {
            return Collections.unmodifiableMap(orders);
        }

        long currentTime = System.currentTimeMillis();

        if (lastVerificationTime != 0 && currentTime - lastVerificationTime < VERIFICATION_THRESHOLD_MS) {
            return Collections.unmodifiableMap(orders);
        }

        lastVerificationTime = currentTime;

        orders.clear();
        getOrdersVerified().forEach(order -> {
            orders.put(order.getOrderId(), order);
        });

        return Collections.unmodifiableMap(orders);
    }

    public void removeOrder(UUID orderId) {
        Order removed = orders.remove(orderId);
        if (removed != null) {
            removeFromIndexes(removed);
        }
    }

    public void load() {
        if (!ValuesManager.getInstance().isCrossServerSupport()) {
            for (Order order : DatabaseManager.getDatabase().getActiveOrders()) {
                orders.put(order.getOrderId(), order);
            }
        }

        OScheduler.async().run(this::rebuildIndexes);
    }

    /**
     * Rebuilds all search indexes asynchronously
     */
    public void rebuildIndexes() {
        CompletableFuture.runAsync(() -> {
            synchronized (this) {
                nameIndex.clear();
                aliasIndex.clear();
                loreIndex.clear();
                categoryIndex.clear();
                orderSearchTerms.clear();
            }

            List<Order> activeOrders = getOrdersVerified();
            for (Order order : activeOrders) {
                indexOrder(order);
            }

            Bukkit.getLogger().info("Search indexes rebuilt: " +
                    nameIndex.size() + " name entries, " +
                    aliasIndex.size() + " alias entries, " +
                    loreIndex.size() + " lore entries for " +
                    activeOrders.size() + " orders");
        });
    }

    private void indexOrder(Order order) {
        ItemStack item = order.getItem();
        if (item == null || item.getType() == null) return;

        Set<String> searchTerms = new HashSet<>();

        String typeName = item.getType().name().toLowerCase();
        nameIndex.put(typeName, order);
        searchTerms.add(typeName);

        for (String part : typeName.split("_")) {
            if (part.length() > 2) {
                nameIndex.put(part, order);
                searchTerms.add(part);
            }
        }

        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase();
            String fullDisplayName = displayName;
            nameIndex.put(fullDisplayName, order);
            searchTerms.add(fullDisplayName);

            for (String word : displayName.split("\\s+")) {
                if (word.length() > 2) {
                    nameIndex.put(word, order);
                    searchTerms.add(word);
                }
            }
        }

        if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
            for (String loreLine : item.getItemMeta().getLore()) {
                String stripped = ChatColor.stripColor(loreLine).toLowerCase();
                for (String word : stripped.split("\\s+")) {
                    if (word.length() > 2) {
                        loreIndex.put(word, order);
                        searchTerms.add(word);
                    }
                }
            }
        }

        ItemPrice itemPrice = PriceManager.getInstance().getItemPrice(item);
        if (itemPrice != null && itemPrice.getAliases() != null) {
            for (String alias : itemPrice.getAliases()) {
                String lowerAlias = alias.toLowerCase();
                aliasIndex.put(lowerAlias, order);
                searchTerms.add(lowerAlias);

                for (String word : lowerAlias.split("\\s+")) {
                    if (word.length() > 2) {
                        aliasIndex.put(word, order);
                        searchTerms.add(word);
                    }
                }
            }
        }

        if (itemPrice != null && !"UNSET".equals(itemPrice.getCategory())) {
            try {
                Filter filter = Filter.valueOf(itemPrice.getCategory());
                categoryIndex.put(filter, order);
            } catch (IllegalArgumentException ignored) {}
        } else {
            Filter category = MaterialManager.getInstance().getCategory(item);
            categoryIndex.put(category, order);
        }

        orderSearchTerms.put(order, searchTerms);
    }

    public boolean isOrderFull(Order order) {
        return order.getTotalAmount() <= order.getDeliveredAmount();
    }

    public List<Order> getOrdersVerified() {
        List<Order> checkIfExpired = new CopyOnWriteArrayList<>();

        if (ValuesManager.getInstance().isCrossServerSupport()) {
            for (Order order : DatabaseManager.getDatabase().getActiveOrders()) {
                if (order.isExpired() || order.isInDeletionThreshold() || isOrderFull(order))
                    continue;

                checkIfExpired.add(order);
            }

            return checkIfExpired;
        }

        if (orders.isEmpty()) {
            for (Order order : DatabaseManager.getDatabase().getActiveOrders()) {
                if (order.isExpired() || order.isInDeletionThreshold() || isOrderFull(order))
                    continue;

                checkIfExpired.add(order);
            }
            return checkIfExpired;
        }

        List<UUID> toRemove = new ArrayList<>();

        for (Order order : orders.values()) {
            if (order.isExpired()) {
                OScheduler.async().run(() -> DatabaseManager.getDatabase().handleExpiredOrder(order));
                toRemove.add(order.getOrderId());
                continue;
            }

            if (order.isInDeletionThreshold()) {
                toRemove.add(order.getOrderId());
                continue;
            }

            if (isOrderFull(order))
                continue;

            checkIfExpired.add(order);
        }

        for (UUID id : toRemove) {
            removeOrder(id);
        }

        return checkIfExpired;
    }

/*
    public List<Order> getPageOrders(int page, Filter filter, SortOrders sort, String search, Player player) {
        List<Order> filtered = filterOrdersUsingIndex(getOrdersVerified(), filter);
        filtered = searchOrdersUsingIndex(search, filtered, player);
        List<Order> sorted = sortOrders(filtered, sort);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, sorted.size());

        if (startIndex >= sorted.size()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(sorted.subList(startIndex, endIndex));
    }
*/

    public List<Order> getPageOrders(
            int page,
            Filter filter,
            SortOrders sort,
            String search,
            Player player,
            int itemsPerPage
    ) {
        List<Order> filtered = filterOrdersUsingIndex(getOrdersVerified(), filter);
        filtered = searchOrdersUsingIndex(search, filtered, player);
        List<Order> sorted = sortOrders(filtered, sort);

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, sorted.size());

        if (startIndex >= sorted.size()) {
            return Collections.emptyList();
        }

        return new ArrayList<>(sorted.subList(startIndex, endIndex));
    }

    public List<Order> getPageOrders(int page, Filter filter, SortOrders sort, String search) {
        return getPageOrders(page, filter, sort, search, null, ITEMS_PER_PAGE);
    }

    private List<Order> filterOrdersUsingIndex(List<Order> orderList, Filter filter) {
        if (filter == Filter.ALL) {
            return new ArrayList<>(orderList);
        }

        Collection<Order> matchingOrders = categoryIndex.get(filter);
        if (!matchingOrders.isEmpty()) {
            Set<Order> orderSet = new HashSet<>(orderList);
            return matchingOrders.stream()
                    .filter(orderSet::contains)
                    .collect(Collectors.toList());
        }

        return filterOrders(orderList, filter);
    }

    private List<Order> filterOrders(List<Order> orderList, Filter filter) {
        if (filter == Filter.ALL) {
            return new ArrayList<>(orderList);
        }

        return orderList.stream()
                .filter(data -> {
                    ItemPrice itemPrice = PriceManager.getInstance().getItemPrice(data.getItem());
                    if (itemPrice != null && !"UNSET".equals(itemPrice.getCategory())) {
                        try {
                            Filter itemFilter = Filter.valueOf(itemPrice.getCategory());
                            return itemFilter == filter;
                        } catch (IllegalArgumentException ignored) {}
                    }

                    return MaterialManager.getInstance().getCategory(data.getItem()) == filter;
                })
                .collect(Collectors.toList());
    }

    private List<Order> searchOrdersUsingIndex(String searchTerm, List<Order> orderList, Player player) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return orderList;
        }

        String lowercaseSearch = searchTerm.toLowerCase();
        Set<Order> matchingOrders = new HashSet<>();

        matchingOrders.addAll(nameIndex.get(lowercaseSearch));
        matchingOrders.addAll(aliasIndex.get(lowercaseSearch));
        matchingOrders.addAll(loreIndex.get(lowercaseSearch));

        String[] searchWords = lowercaseSearch.split("\\s+");
        if (searchWords.length > 1) {
            Set<Order> candidateOrders = new HashSet<>();

            candidateOrders.addAll(nameIndex.get(searchWords[0]));
            candidateOrders.addAll(aliasIndex.get(searchWords[0]));
            candidateOrders.addAll(loreIndex.get(searchWords[0]));

            for (Order order : candidateOrders) {
                Set<String> terms = orderSearchTerms.get(order);
                if (terms != null) {
                    boolean hasAllWords = true;
                    for (String word : searchWords) {
                        if (word.length() > 2 && !terms.contains(word)) {
                            hasAllWords = false;
                            break;
                        }
                    }
                    if (hasAllWords) {
                        matchingOrders.add(order);
                    }
                }
            }
        }

        for (Map.Entry<String, Order> entry : nameIndex.entries()) {
            if (entry.getKey().contains(lowercaseSearch)) {
                matchingOrders.add(entry.getValue());
            }
        }

        for (Map.Entry<String, Order> entry : aliasIndex.entries()) {
            if (entry.getKey().contains(lowercaseSearch)) {
                matchingOrders.add(entry.getValue());
            }
        }

        if (player != null) {
            PlayerQueryItemEvent event = new PlayerQueryItemEvent(player, searchTerm);
            Bukkit.getPluginManager().callEvent(event);
            List<ItemStack> translatedItems = event.getList().getItems();

            for (ItemStack translatedItem : translatedItems) {
                for (Order order : orderList) {
                    if (order.getItem().isSimilar(translatedItem)) {
                        matchingOrders.add(order);
                    }
                }
            }
        }

        Set<Order> orderSet = new HashSet<>(orderList);
        List<Order> result = matchingOrders.stream()
                .filter(orderSet::contains)
                .distinct()
                .collect(Collectors.toList());

        if (!result.isEmpty()) {
            return result;
        }

        return searchOrders(searchTerm, orderList, player);
    }

    private List<Order> searchOrders(String searchTerm, List<Order> orderList, Player player) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return orderList;
        }

        String lowercaseSearch = searchTerm.toLowerCase();

        List<ItemStack> translatedItems = new ArrayList<>();
        if (player != null) {
            PlayerQueryItemEvent event = new PlayerQueryItemEvent(player, searchTerm);
            Bukkit.getPluginManager().callEvent(event);
            translatedItems = event.getList().getItems();
        }

        final List<ItemStack> finalTranslatedItems = translatedItems;

        return orderList.stream()
                .filter(order -> {
                    ItemStack item = order.getItem();

                    for (ItemStack translatedItem : finalTranslatedItems) {
                        if (item.isSimilar(translatedItem)) {
                            return true;
                        }
                    }

                    if (item.getType().name().toLowerCase().contains(lowercaseSearch)) {
                        return true;
                    }

                    if (item.hasItemMeta() && item.getItemMeta().hasDisplayName() &&
                            ChatColor.stripColor(item.getItemMeta().getDisplayName().toLowerCase()).contains(lowercaseSearch)) {
                        return true;
                    }

                    if (item.hasItemMeta() && item.getItemMeta().hasLore()) {
                        for (String loreLine : item.getItemMeta().getLore()) {
                            if (ChatColor.stripColor(loreLine.toLowerCase()).contains(lowercaseSearch)) {
                                return true;
                            }
                        }
                    }

                    ItemPrice itemPrice = PriceManager.getInstance().getItemPrice(item);
                    if (itemPrice != null) {
                        if (itemPrice.getAliases() != null) {
                            for (String alias : itemPrice.getAliases()) {
                                if (alias.toLowerCase().contains(lowercaseSearch)) {
                                    return true;
                                }
                            }
                        }

                        if (itemPrice.getCategory() != null &&
                                !itemPrice.getCategory().equals("UNSET") &&
                                itemPrice.getCategory().toLowerCase().contains(lowercaseSearch)) {
                            return true;
                        }
                    }

                    return false;
                })
                .collect(Collectors.toList());
    }

    private List<Order> sortOrders(List<Order> orderList, SortOrders sort) {
        return switch (sort) {
            case MOST_PAID -> orderList.stream()
                    .sorted(Comparator.comparing(Order::getTotalCost).reversed())
                    .collect(Collectors.toList());
            case MOST_DELIVERED -> orderList.stream()
                    .sorted(Comparator.comparing(Order::getDeliveredAmount).reversed())
                    .collect(Collectors.toList());
            case RECENTLY_LISTED -> orderList.stream()
                    .sorted(Comparator.comparing(Order::getRemainingTime).reversed())
                    .collect(Collectors.toList());
            case MOST_MONEY_PER_ITEM -> orderList.stream()
                    .sorted(Comparator.comparing(Order::getPricePerItem).reversed())
                    .collect(Collectors.toList());
        };
    }

/*    public int getMaxPages(Filter filter, String search, Player player) {
//        List<Order> filtered = filterOrdersUsingIndex(getOrdersVerified(), filter);
        List<Order> filtered = filterOrdersUsingIndex(getOrdersVerified(), filter);
        filtered = searchOrdersUsingIndex(search, filtered, player);
        return (int) Math.ceil((double) filtered.size() / ITEMS_PER_PAGE);
    }*/
    public int getMaxPages(Filter filter, String search, Player player, int itemsPerPage) {
        List<Order> filtered = filterOrdersUsingIndex(getOrdersVerified(), filter);
        filtered = searchOrdersUsingIndex(search, filtered, player);
        return (int) Math.ceil((double) filtered.size() / itemsPerPage);
    }

    public int getMaxPages(Filter filter, String search) {
        return getMaxPages(filter, search, null, ITEMS_PER_PAGE);
    }

    public void updateOrder(Order order) {
        Order old = orders.put(order.getOrderId(), order);
        if (old != null && old != order) {
            removeFromIndexes(old);
        }

        indexOrder(order);

        if (ValuesManager.getInstance().isCrossServerSupport()) {
            DatabaseManager.getDatabase().updateOrder(order);
        }
    }

    private void removeFromIndexes(Order order) {
        nameIndex.values().remove(order);
        aliasIndex.values().remove(order);
        loreIndex.values().remove(order);
        categoryIndex.values().remove(order);
        orderSearchTerms.remove(order);
    }
}
