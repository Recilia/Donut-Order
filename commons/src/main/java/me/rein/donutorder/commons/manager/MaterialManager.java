package me.rein.donutorder.commons.manager;

import lombok.Getter;
import me.rein.donutorder.api.event.PlayerQueryItemEvent;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.commons.util.helper.MMOItemsHelper;
import me.rein.donutorder.commons.util.xseries.XUtil;
import me.rein.donutorder.commons.manager.price.ItemPrice;
import me.rein.donutorder.commons.manager.price.PriceManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/*
 * I'm gonna be honest, it's been a long time since
 * I really worked on donutorder, this class isn't my best work,
 * I apologize beforehand for all the trouble when trying to read this
 */
public class MaterialManager {
    private static class LazyHolder {
        public static final MaterialManager INSTANCE = new MaterialManager();
    }

    public static MaterialManager getInstance() {
        return LazyHolder.INSTANCE;
    }

    private MaterialManager() {}

    @Getter
    private final Map<ItemStack, MaterialData> materials = new ConcurrentHashMap<>();
    private static final int ITEMS_PER_PAGE = 45;

    // Indexes
    private final Map<Filter, List<MaterialData>> categoryIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<MaterialData>> nameIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<MaterialData>> aliasIndex = new ConcurrentHashMap<>();
    private final Map<String, Set<MaterialData>> trigramIndex = new ConcurrentHashMap<>();

    private final Map<SearchCacheKey, List<MaterialData>> searchCache = new ConcurrentHashMap<>();
    private static final int MAX_CACHE_SIZE = 200;

    private final Set<MaterialData> tempResults = ConcurrentHashMap.newKeySet();

    public void clearMaterialMap() {
        materials.clear();
        categoryIndex.clear();
        nameIndex.clear();
        aliasIndex.clear();
        trigramIndex.clear();
        searchCache.clear();
    }

    public void initializeMaterials(Map<ItemStack, Double> itemStackWithPrices) {
        clearMaterialMap();

        if (itemStackWithPrices == null || itemStackWithPrices.isEmpty()) {
            Bukkit.getLogger().warning("No items to initialize in MaterialManager");
            return;
        }

        for (Map.Entry<ItemStack, Double> entry : itemStackWithPrices.entrySet()) {
            try {
                ItemStack item = entry.getKey();
                if (item == null || item.getType() == null || item.getType() == Material.AIR) {
                    continue;
                }

                Double price = entry.getValue();
                if (price == null) {
                    continue;
                }

                Filter category = getCategory(item);
                MaterialData materialData = new MaterialData(item, category, price.intValue());
                materials.put(item, materialData);
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error adding item to materials: " + e.getMessage());
            }
        }

        CompletableFuture.runAsync(() -> {
            try {
                Bukkit.getLogger().info("Starting material indexing for " + materials.size() + " items...");
                long startTime = System.currentTimeMillis();

                Map<Filter, List<MaterialData>> tempCategoryIndex = new HashMap<>();
                for (MaterialData data : materials.values()) {
                    if (data == null || data.getCategory() == null) continue;
                    tempCategoryIndex.computeIfAbsent(data.getCategory(), k -> new ArrayList<>()).add(data);
                }

                for (Map.Entry<Filter, List<MaterialData>> entry : tempCategoryIndex.entrySet()) {
                    categoryIndex.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }

                Map<String, Set<MaterialData>> tempNameIndex = new HashMap<>();
                Map<String, Set<MaterialData>> tempAliasIndex = new HashMap<>();
                Map<String, Set<MaterialData>> tempTrigramIndex = new HashMap<>();

                List<MaterialData> allData = new ArrayList<>(materials.values());
                int totalItems = allData.size();
                int chunkSize = 500;
                int indexed = 0;

                for (int i = 0; i < totalItems; i += chunkSize) {
                    int endIndex = Math.min(i + chunkSize, totalItems);
                    List<MaterialData> chunk = allData.subList(i, endIndex);

                    for (MaterialData data : chunk) {
                        if (data == null || data.getItem() == null)
                            continue;

                        indexItemName(data.getItem(), data, tempNameIndex, tempTrigramIndex);

                        indexItemAliases(data.getItem(), data, tempAliasIndex, tempTrigramIndex);
                    }

                    indexed += chunk.size();
                    if (i + chunkSize < totalItems) {
                        Bukkit.getLogger().info("Indexed " + indexed + "/" + totalItems + " items...");
                        try {
                            Thread.sleep(5);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }
                }

                updateIndexFromTemp(tempNameIndex, nameIndex);
                updateIndexFromTemp(tempAliasIndex, aliasIndex);
                updateIndexFromTemp(tempTrigramIndex, trigramIndex);

                long endTime = System.currentTimeMillis();
                Bukkit.getLogger().info("Material indexing complete: " + materials.size() + " materials indexed in " +
                        (endTime - startTime) + "ms with " + nameIndex.size() + " name entries, " +
                        aliasIndex.size() + " alias entries, and " + trigramIndex.size() + " trigram entries");
            } catch (Exception e) {
                Bukkit.getLogger().severe("Error building material indexes: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void updateIndexFromTemp(Map<String, Set<MaterialData>> tempIndex, Map<String, Set<MaterialData>> targetIndex) {
        for (Map.Entry<String, Set<MaterialData>> entry : tempIndex.entrySet()) {
            targetIndex.put(entry.getKey(), ConcurrentHashMap.newKeySet());
            targetIndex.get(entry.getKey()).addAll(entry.getValue());
        }
    }

    private void indexItemName(
            ItemStack item,
            MaterialData materialData,
            Map<String, Set<MaterialData>> indexMap,
            Map<String, Set<MaterialData>> trigramMap
    ) {
        try {
            if (item == null || item.getType() == null || materialData == null) {
                return;
            }

            String typeName = item.getType().name().toLowerCase();
            addToIndex(indexMap, typeName, materialData);
            indexTrigrams(typeName, materialData, trigramMap);

            for (String part : typeName.split("_")) {
                if (part != null && part.length() > 2) {
                    addToIndex(indexMap, part, materialData);
                    indexTrigrams(part, materialData, trigramMap);
                }
            }

            if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
                String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName().toLowerCase());
                if (displayName != null) {
                    addToIndex(indexMap, displayName, materialData);
                    indexTrigrams(displayName, materialData, trigramMap);

                    for (String word : displayName.split("\\s+")) {
                        if (word != null && word.length() > 2) {
                            addToIndex(indexMap, word, materialData);
                            indexTrigrams(word, materialData, trigramMap);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error indexing item name: " + e.getMessage());
        }
    }

    private void indexItemAliases(ItemStack item, MaterialData materialData,
                                  Map<String, Set<MaterialData>> indexMap,
                                  Map<String, Set<MaterialData>> trigramMap) {
        try {
            if (item == null || materialData == null) {
                return;
            }

            ItemPrice itemPrice = PriceManager.getInstance().getItemPrice(item);
            if (itemPrice != null && itemPrice.getAliases() != null) {
                for (String alias : itemPrice.getAliases()) {
                    if (alias == null) continue;

                    String lowerAlias = alias.toLowerCase();
                    addToIndex(indexMap, lowerAlias, materialData);
                    indexTrigrams(lowerAlias, materialData, trigramMap);

                    for (String word : lowerAlias.split("\\s+")) {
                        if (word != null && word.length() > 2) {
                            addToIndex(indexMap, word, materialData);
                            indexTrigrams(word, materialData, trigramMap);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error indexing item aliases: " + e.getMessage());
        }
    }

    private void indexTrigrams(String text, MaterialData data, Map<String, Set<MaterialData>> trigramMap) {
        if (text == null || text.length() < 3) return;

        for (int i = 0; i <= text.length() - 3; i++) {
            String trigram = text.substring(i, i + 3);
            addToIndex(trigramMap, trigram, data);
        }
    }

    private void addToIndex(Map<String, Set<MaterialData>> index, String key, MaterialData data) {
        if (key == null || key.isEmpty() || data == null || index == null) return;
        index.computeIfAbsent(key, k -> new HashSet<>()).add(data);
    }

    public boolean hasPricedMaterial(Material material) {
        if (material == null) return false;
        return nameIndex.containsKey(material.name().toLowerCase());
    }

    public List<ItemStack> getPageMaterials(int page, Filter filter, SortSelectItem sort, String search) {
        return getPageMaterials(page, filter, sort, search, null, ITEMS_PER_PAGE);
    }

    public List<ItemStack> getPageMaterials(int page, Filter filter, SortSelectItem sort, String search, Player player) {
        return getPageMaterials(page, filter, sort, search, player, ITEMS_PER_PAGE);
    }

    public List<ItemStack> getPageMaterials(int page, Filter filter, SortSelectItem sort, String search, Player player, int itemsPerPage) {
        try {
            if (materials.isEmpty()) {
                return Collections.emptyList();
            }

            if (page < 1) page = 1;
            if (filter == null) filter = Filter.ALL;
            if (sort == null) sort = SortSelectItem.A_Z;

            SearchCacheKey cacheKey = new SearchCacheKey(filter, sort, search);
            List<MaterialData> filtered = getFilteredAndSearchedMaterials(filter, search, player, cacheKey);

            if (filtered.isEmpty()) {
                return Collections.emptyList();
            }

            if (!searchCache.containsKey(cacheKey)) {
                filtered = sortMaterials(filtered, sort);
                if (searchCache.size() < MAX_CACHE_SIZE) {
                    searchCache.put(cacheKey, filtered);
                }
            }

            int startIndex = (page - 1) * itemsPerPage;
            if (startIndex >= filtered.size()) {
                if (page > 1) {
                    startIndex = ((filtered.size() - 1) / itemsPerPage) * itemsPerPage;
                } else {
                    return Collections.emptyList();
                }
            }

            int endIndex = Math.min(startIndex + itemsPerPage, filtered.size());

            return filtered.subList(startIndex, endIndex).stream()
                    .filter(Objects::nonNull)
                    .map(MaterialData::getItem)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            Bukkit.getLogger().severe("Error in getPageMaterials: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }

    private List<MaterialData> getFilteredAndSearchedMaterials(Filter filter, String search, Player player, SearchCacheKey cacheKey) {
        List<MaterialData> cached = searchCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }

        List<MaterialData> filtered = getFilteredMaterials(filter);
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        if (search != null && !search.isEmpty()) {
            return searchMaterials(search, filtered, player);
        }

        return filtered;
    }

    private List<MaterialData> getFilteredMaterials(Filter filter) {
        if (filter == null) return new ArrayList<>(materials.values());

        if (filter == Filter.ALL) {
            return materials.values().stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
        }

        List<MaterialData> result = categoryIndex.get(filter);
        if (result != null && !result.isEmpty()) {
            return new ArrayList<>(result);
        }

        return materials.values().stream()
                .filter(Objects::nonNull)
                .filter(data -> data.getCategory() == filter)
                .collect(Collectors.toList());
    }

    private List<MaterialData> searchMaterials(String searchTerm, List<MaterialData> materialsList, Player player) {
        if (searchTerm == null || searchTerm.isEmpty()) {
            return materialsList;
        }

        if (materialsList == null || materialsList.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            tempResults.clear();
            String lowercaseSearch = searchTerm.toLowerCase();

            if (lowercaseSearch.length() >= 3 && !hasAnyMatchingTrigram(lowercaseSearch)) {
                return Collections.emptyList();
            }

            if (player != null) {
                PlayerQueryItemEvent event = new PlayerQueryItemEvent(player, searchTerm);
                Bukkit.getPluginManager().callEvent(event);

                List<ItemStack> translatedItems = event.getList().getItems();
                if (translatedItems != null) {
                    for (ItemStack translatedItem : translatedItems) {
                        if (translatedItem == null) continue;
                        MaterialData data = materials.get(translatedItem);
                        if (data != null) {
                            tempResults.add(data);
                        }
                    }
                }
            }

            if (tempResults.isEmpty()) {
                findExactOrWordMatches(nameIndex, lowercaseSearch, tempResults);
                findExactOrWordMatches(aliasIndex, lowercaseSearch, tempResults);
            }

            if (tempResults.isEmpty() && lowercaseSearch.length() >= 3) {
                findTrigramMatches(lowercaseSearch, tempResults);
            }

            if (tempResults.isEmpty()) {
                return materialsList.stream()
                        .filter(Objects::nonNull)
                        .filter(data -> matchesSearch(data, lowercaseSearch))
                        .collect(Collectors.toList());
            }

            Set<ItemStack> originalItems = materialsList.stream()
                    .filter(Objects::nonNull)
                    .map(MaterialData::getItem)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            return tempResults.stream()
                    .filter(Objects::nonNull)
                    .filter(data -> data.getItem() != null && originalItems.contains(data.getItem()))
                    .collect(Collectors.toList());

        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in search: " + e.getMessage());
            return materialsList.stream().filter(Objects::nonNull).collect(Collectors.toList());
        }
    }

    private boolean hasAnyMatchingTrigram(String searchTerm) {
        if (searchTerm == null || searchTerm.length() < 3 || trigramIndex.isEmpty())
            return true; // We assume it's the right match

        for (int i = 0; i <= searchTerm.length() - 3; i++) {
            String trigram = searchTerm.substring(i, i + 3);
            if (trigramIndex.containsKey(trigram)) {
                return true;
            }
        }

        return false;
    }

    private void findExactOrWordMatches(Map<String, Set<MaterialData>> index, String searchTerm, Set<MaterialData> results) {
        Set<MaterialData> exactMatches = index.get(searchTerm);
        if (exactMatches != null) {
            results.addAll(exactMatches);
        }

        if (results.isEmpty() && searchTerm.contains(" ")) {
            for (String word : searchTerm.split("\\s+")) {
                if (word != null && word.length() > 2) {
                    Set<MaterialData> wordMatches = index.get(word);
                    if (wordMatches != null) {
                        results.addAll(wordMatches);
                    }
                }
            }
        }
    }

    private void findTrigramMatches(String searchTerm, Set<MaterialData> results) {
        Set<MaterialData> candidates = new HashSet<>();
        Set<String> processedTrigrams = new HashSet<>();

        for (int i = 0; i <= searchTerm.length() - 3; i++) {
            String trigram = searchTerm.substring(i, i + 3);
            if (processedTrigrams.contains(trigram)) continue;

            processedTrigrams.add(trigram);
            Set<MaterialData> trigramMatches = trigramIndex.get(trigram);
            if (trigramMatches != null) {
                candidates.addAll(trigramMatches);

                if (candidates.size() > 500)
                    break;
            }
        }

        int count = 0;
        for (MaterialData candidate : candidates) {
            if (matchesSearch(candidate, searchTerm)) {
                results.add(candidate);
                if (++count >= 100) break;
            }
        }
    }

    private boolean matchesSearch(MaterialData data, String searchTerm) {
        if (data == null || searchTerm == null) return false;

        ItemStack item = data.getItem();
        if (item == null || item.getType() == null) return false;

        if (item.getType().name().toLowerCase().contains(searchTerm)) {
            return true;
        }

        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasDisplayName()) {
            String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName().toLowerCase());
            if (displayName != null && displayName.contains(searchTerm)) {
                return true;
            }
        }

        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasLore()) {
            List<String> lore = item.getItemMeta().getLore();
            if (lore != null) {
                for (String loreLine : lore) {
                    if (loreLine != null && ChatColor.stripColor(loreLine.toLowerCase()).contains(searchTerm)) {
                        return true;
                    }
                }
            }
        }

        ItemPrice itemPrice = PriceManager.getInstance().getItemPrice(item);
        if (itemPrice != null) {
            if (itemPrice.getAliases() != null) {
                for (String alias : itemPrice.getAliases()) {
                    if (alias != null && alias.toLowerCase().contains(searchTerm)) {
                        return true;
                    }
                }
            }

            if (itemPrice.getCategory() != null &&
                    !itemPrice.getCategory().equals("UNSET") &&
                    itemPrice.getCategory().toLowerCase().contains(searchTerm)) {
                return true;
            }
        }

        return false;
    }

    private List<MaterialData> sortMaterials(List<MaterialData> materials, SortSelectItem sort) {
        if (materials == null || materials.isEmpty()) {
            return Collections.emptyList();
        }

        if (sort == null)
            sort = SortSelectItem.A_Z;

        try {
            return switch (sort) {
                case A_Z -> materials.stream()
                        .filter(data -> data != null && data.getItem() != null && data.getItem().getType() != null)
                        .sorted(Comparator.comparing(data ->
                                data.getItem().getType().name()))
                        .collect(Collectors.toList());
                case Z_A -> materials.stream()
                        .filter(data -> data != null && data.getItem() != null && data.getItem().getType() != null)
                        .sorted(Comparator.comparing(data ->
                                        data.getItem().getType().name(),
                                Comparator.reverseOrder()))
                        .collect(Collectors.toList());
                case HIGHEST_PRICE -> materials.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(MaterialData::getPrice)
                                .reversed())
                        .collect(Collectors.toList());
                case LOWEST_PRICE -> materials.stream()
                        .filter(Objects::nonNull)
                        .sorted(Comparator.comparingInt(MaterialData::getPrice))
                        .collect(Collectors.toList());
                default -> materials.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toList());
            };
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in sortMaterials: " + e.getMessage());
            return materials.stream()
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()); // Return filtered list on error
        }
    }

    public int getMaxPages(Filter filter, String search) {
        return getMaxPages(filter, search, null, ITEMS_PER_PAGE);
    }

    public int getMaxPages(Filter filter, String search, Player player) {
        return getMaxPages(filter, search, player, ITEMS_PER_PAGE);
    }

    public int getMaxPages(Filter filter, String search, Player player, int itemsPerPage) {
        try {
            if (filter == null) filter = Filter.ALL;

            SearchCacheKey cacheKey = new SearchCacheKey(filter, null, search);
            List<MaterialData> filtered = getFilteredAndSearchedMaterials(filter, search, player, cacheKey);

            return Math.max(1, (int) Math.ceil((double) filtered.size() / itemsPerPage));
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in getMaxPages: " + e.getMessage());
            return 1;
        }
    }

    public Filter getCategory(ItemStack item) {
        if (item == null || item.getType() == null) return Filter.UTILITIES;

        try {
            String name = item.getType().name();
            Material material = item.getType();

            if (item.getType() == XUtil.crossMaterial("POTION") ||
                    item.getType() == XUtil.crossMaterial("SPLASH_POTION") ||
                    item.getType() == XUtil.crossMaterial("LINGERING_POTION")) {
                return Filter.POTIONS;
            }

            if (item.getType() == XUtil.crossMaterial("ENCHANTED_BOOK")) {
                return Filter.BOOKS;
            }

            if (name.contains("SHIELD") ||
                    name.contains("FISHING_ROD") ||
                    name.contains("_HOE") ||
                    name.equals("ELYTRA") ||
                    name.contains("COMPASS") ||
                    name.contains("FLINT_AND_STEEL") ||
                    name.contains("CLOCK") ||
                    name.contains("BRUSH")) {
                return Filter.TOOLS;
            }

            // Combat
            if (name.contains("SWORD") ||
                    name.contains("BOW") ||
                    name.contains("ARROW") ||
                    name.contains("_ARMOR") ||
                    name.contains("HELMET") ||
                    name.contains("CHESTPLATE") ||
                    name.contains("LEGGINGS") ||
                    name.contains("BOOTS") ||
                    name.equals("END_CRYSTAL") ||
                    name.equals("SNOWBALL") ||
                    name.equals("TNT")) {
                return Filter.COMBAT;
            }

            // Potions
            if (name.contains("POTION") ||
                    name.contains("SPLASH") ||
                    name.contains("LINGERING")) {
                return Filter.POTIONS;
            }

            // Blocks (only actual placeable blocks)
            if (material.isBlock() &&
                    !name.contains("SIGN") &&
                    !name.contains("BANNER")) {
                return Filter.BLOCKS;
            }

            // Food
            if (material.isEdible()) {
                return Filter.FOOD;
            }

            // Books
            if (name.contains("BOOK")) {
                return Filter.BOOKS;
            }

            // Ingredients
            if (name.contains("INGREDIENT") ||
                    name.contains("DUST") ||
                    name.contains("NUGGET") ||
                    name.contains("SHARD") ||
                    name.contains("POWDER")) {
                return Filter.INGREDIENTS;
            }

            return Filter.UTILITIES;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error determining category: " + e.getMessage());
            return Filter.UTILITIES; // Default category on error
        }
    }

    public boolean isEnchantable(ItemStack item) {
        if (item == null || item.getType() == null) return false;

        try {
            Material type = item.getType();
            String name = type.name();

            if (type == XUtil.crossMaterial("ENCHANTED_BOOK"))
                return false;

            // Tools
            if (name.endsWith("_PICKAXE") ||
                    name.endsWith("_AXE") ||
                    name.endsWith("_SHOVEL") ||
                    name.endsWith("_HOE") ||
                    name.contains("FISHING_ROD") ||
                    name.contains("SHEARS") ||
                    name.contains("FLINT_AND_STEEL")) {
                return true;
            }

            // Weapons and Armor
            if (name.endsWith("_SWORD") ||
                    name.contains("BOW") ||
                    name.contains("CROSSBOW") ||
                    name.contains("TRIDENT") ||
                    name.endsWith("_HELMET") ||
                    name.endsWith("_CHESTPLATE") ||
                    name.endsWith("_LEGGINGS") ||
                    name.endsWith("_BOOTS") ||
                    name.contains("SHIELD")) {
                return true;
            }

            // Books
            if (type == XUtil.crossMaterial("BOOK")) {
                return true;
            }

            // Wearables
            if (name.contains("ELYTRA")) {
                return true;
            }

            // Special items
            if (name.contains("TURTLE_HELMET") ||
                    name.contains("CARROT_ON_A_STICK") ||
                    name.contains("WARPED_FUNGUS_ON_A_STICK")) {
                return true;
            }

            return false;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in isEnchantable: " + e.getMessage());
            return false;
        }
    }

    @Getter
    public static class MaterialData {
        private final ItemStack item;
        private final Filter category;
        private final int price;
        private final String mmoItemType;
        private final String mmoItemId;

        public MaterialData(ItemStack item, Filter category, int price) {
            this.item = item;
            this.category = category;
            this.price = price;

            if (MMOItemsHelper.isMMOItem(item)) {
                this.mmoItemType = MMOItemsHelper.getMMOItemType(item);
                this.mmoItemId = MMOItemsHelper.getMMOItemId(item);
            } else {
                this.mmoItemType = null;
                this.mmoItemId = null;
            }
        }

        public boolean isMMOItem() {
            return mmoItemType != null && mmoItemId != null;
        }

        public ItemStack getItem() {
            if (isMMOItem()) {
                ItemStack refreshed = MMOItemsHelper.getMMOItem(mmoItemType, mmoItemId);
                return refreshed != null ? refreshed : item;
            }

            return item;
        }
    }

    private static class SearchCacheKey {
        private final Filter filter;
        private final SortSelectItem sort;
        private final String search;

        public SearchCacheKey(Filter filter, SortSelectItem sort, String search) {
            this.filter = filter;
            this.sort = sort;
            this.search = search;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SearchCacheKey that = (SearchCacheKey) o;
            return filter == that.filter &&
                    sort == that.sort &&
                    Objects.equals(search, that.search);
        }

        @Override
        public int hashCode() {
            return Objects.hash(filter, sort, search);
        }
    }
}
