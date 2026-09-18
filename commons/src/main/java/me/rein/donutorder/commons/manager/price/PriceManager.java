package me.rein.donutorder.commons.manager.price;

import lombok.Getter;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.util.helper.MMOItemsHelper;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import me.rein.donutorder.commons.manager.BlacklistManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.misc.ATest;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Registry;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.material.MaterialData;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Getter
public class PriceManager {

    private final Map<ItemStack, Double> prices = new ConcurrentHashMap<>();
    private final Map<String, ItemPrice> itemPrices = new ConcurrentHashMap<>();
    private final Map<ItemStack, ItemPrice> itemLink = new ConcurrentHashMap<>();
    private final Set<String> pendingSaves = ConcurrentHashMap.newKeySet();
    private final AtomicBoolean saveInProgress = new AtomicBoolean(false);
    private static final int BATCH_SIZE = 50;

    public static class LazyHolder {
        public static final PriceManager INSTANCE = new PriceManager();
    }

    public static PriceManager getInstance() {
        return PriceManager.LazyHolder.INSTANCE;
    }

    private PriceManager() {}

    public void load() {
        prices.clear();
        itemPrices.clear();
        pendingSaves.clear();

        ATest.test();

        boolean needsMigration = ConfigSelector.PRICESV2.getConfig().getStringList("prices").isEmpty() &&
                !ConfigSelector.PRICES.getConfig().getStringList("prices").isEmpty();

        if (needsMigration) {
            PriceMigrator.migrateToV2();
        }

        List<String> itemKeysList = ConfigSelector.PRICESV2.getConfig().getStringList("prices");

        for (String itemKey : itemKeysList) {
            try {
                if (itemKey == null || itemKey.isEmpty()) {
                    Bukkit.getLogger().warning("Skipping null or empty item key");
                    continue;
                }

                ConfigurationSection itemSection = ConfigSelector.PRICESV2.getConfig().getConfigurationSection(itemKey);
                if (itemSection == null) {
                    Bukkit.getLogger().warning("No section found for item: " + itemKey);
                    continue;
                }

                if (BlacklistManager.getInstance().isBlacklisted(itemKey)) {
                    continue;
                }

                ItemStack item = null;
                if (itemSection.contains("mmoitem.type") && itemSection.contains("mmoitem.id")) {
                    String mmoType = itemSection.getString("mmoitem.type");
                    String mmoId = itemSection.getString("mmoitem.id");

                    item = MMOItemsHelper.getMMOItem(mmoType, mmoId);

                    if (item != null) {
                        Bukkit.getLogger().info("Loaded MMOItem: " + mmoType + ":" + mmoId);
                    }
                }

                if (item == null) {
                    ConfigurationSection itemDataSection = itemSection.getConfigurationSection("item");
                    if (itemDataSection == null) {
                        Bukkit.getLogger().warning("No item data found for: " + itemKey);
                        continue;
                    }

                    item = deserialize(itemDataSection.getValues(false));;
                }

                if (item == null || item.getType() == Material.AIR) {
                    Bukkit.getLogger().warning("Invalid material for item: " + itemKey);
                    continue;
                }

                double amount = itemSection.getDouble("price", -1);
                if (amount == -1) {
                    continue;
                }

                List<String> aliases = itemSection.getStringList("aliases");
                if (aliases == null) {
                    aliases = new ArrayList<>();
                }

                String category = itemSection.getString("category", "UNSET");

                ItemPrice itemPrice = new ItemPrice(item, amount, itemKey, aliases, category);

                boolean isDuplicate = false;
                for (Map.Entry<ItemStack, Double> entry : prices.entrySet()) {
                    if (entry.getKey() != null && ItemUtil.hasSimilarMetadata(entry.getKey(), item)) {
                        isDuplicate = true;
                        if (amount > entry.getValue()) {
                            prices.put(entry.getKey(), amount);
                        }
                        break;
                    }
                }
                if (!isDuplicate) {
                    prices.put(item, amount);
                }

                itemPrices.put(itemKey, itemPrice);
                itemLink.put(item, itemPrice);

            } catch (Exception e) {
                Bukkit.getLogger().severe("Error loading item: " + itemKey + " - " + e.getMessage());
                e.printStackTrace();
            }
        }

        /*
         * Enchantments - process in memory
         */
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            try {
                if (enchantment == null) continue;

                ItemStack book = new ItemStack(XUtil.crossMaterial("ENCHANTED_BOOK"));
                EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
                if (meta == null) continue;

                meta.addStoredEnchant(enchantment, enchantment.getMaxLevel(), true);
                book.setItemMeta(meta);

                boolean exists = false;
                for (ItemStack existingItem : prices.keySet()) {
                    if (ItemUtil.hasSimilarMetadata(existingItem, book)) {
                        exists = true;
                        break;
                    }
                }

                if (!exists && !ValuesManager.getInstance().isStopAutoMigrationOnStart()) {
                    String enchKey = "enchant_" + enchantment.getKey().getKey();
                    ItemPrice enchantPrice = new ItemPrice(book, 200.0, enchKey, new ArrayList<>(), "ENCHANTMENTS");

                    prices.put(book, 200.0);
                    itemPrices.put(enchKey, enchantPrice);

                    if (!ConfigSelector.PRICESV2.getConfig().contains(enchKey)) {
                        pendingSaves.add(enchKey);
                    }
                }
            } catch (Exception e) {
                Bukkit.getLogger().warning("Error adding enchantment: " + e.getMessage());
            }
        }

        if (!pendingSaves.isEmpty()) {
            scheduleBatchSave();
        }

    }

    private void scheduleBatchSave() {
        if (saveInProgress.compareAndSet(false, true)) {
            OScheduler.sync().run(this::processBatchSave);
        }
    }

    private void processBatchSave() {
        if (pendingSaves.isEmpty()) {
            saveInProgress.set(false);
            return;
        }

        List<String> batch = new ArrayList<>();
        Iterator<String> iterator = pendingSaves.iterator();
        int count = 0;

        while (iterator.hasNext() && count < BATCH_SIZE) {
            batch.add(iterator.next());
            iterator.remove();
            count++;
        }

        try {
            List<String> pricesList = ConfigSelector.PRICESV2.getConfig().getStringList("prices");
            boolean pricesListChanged = false;

            for (String key : batch) {
                ItemPrice itemPrice = itemPrices.get(key);
                if (itemPrice == null) continue;

                ConfigurationSection itemSection = ConfigSelector.PRICESV2.getConfig().createSection(key);

                if (itemPrice.isMMOItem()) {
                    itemSection.set("mmoitem.type", itemPrice.getMmoItemType());
                    itemSection.set("mmoitem.id", itemPrice.getMmoItemId());
                }

                itemSection.set("item", itemPrice.getItem().serialize());
                itemSection.set("price", itemPrice.getPrice());
                itemSection.set("aliases", itemPrice.getAliases());
                itemSection.set("category", itemPrice.getCategory());

                if (!pricesList.contains(key)) {
                    pricesList.add(key);
                    pricesListChanged = true;
                }
            }

            if (pricesListChanged) {
                ConfigSelector.PRICESV2.getConfig().set("prices", pricesList);
            }

            ConfigSelector.PRICESV2.saveConfig();
            updateConfig();

            if (!pendingSaves.isEmpty()) {
                OScheduler.sync().runLater(this::processBatchSave, 1L);
            } else {
                saveInProgress.set(false);
            }
        } catch (Exception e) {
            Bukkit.getLogger().severe("Error in batch save: " + e.getMessage());
            saveInProgress.set(false);
        }
    }

    public ItemStack getItemFromConfig(String name) {
        if (name == null || name.isEmpty()) return null;

        ConfigurationSection itemSection = ConfigSelector.PRICESV2.getConfig().getConfigurationSection(name);
        if (itemSection != null) {
            ConfigurationSection itemDataSection = itemSection.getConfigurationSection("item");
            if (itemDataSection != null) {
                try {
                    return deserialize(itemDataSection.getValues(false));
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Error deserializing item from config: " + e.getMessage());
                    return null;
                }
            }
        }
        return null;
    }

    public double getPrice(ItemStack item) {
        if (item == null || item.getType() == null) return -1.0;

        double basePrice = -1.0;
        double enchantBonus = 0.0;

        for (Map.Entry<ItemStack, Double> entry : prices.entrySet()) {
            if (entry.getKey() == null || entry.getValue() == null) continue;

            if (entry.getKey().isSimilar(item)) {
                return entry.getValue();
            }
            if (entry.getKey().getType() == item.getType() &&
                    !entry.getKey().hasItemMeta()) {
                basePrice = entry.getValue();
            }
        }

        if (item.hasItemMeta() && item.getItemMeta() != null && item.getItemMeta().hasEnchants()) {
            for (Map.Entry<Enchantment, Integer> enchant : item.getEnchantments().entrySet()) {
                if (enchant.getKey() == null) continue;

                try {
                    ItemStack enchantedBook = new ItemStack( XUtil.crossMaterial("ENCHANTED_BOOK"));
                    EnchantmentStorageMeta meta = (EnchantmentStorageMeta) enchantedBook.getItemMeta();
                    if (meta == null) continue;

                    meta.addStoredEnchant(enchant.getKey(), enchant.getValue(), true);
                    enchantedBook.setItemMeta(meta);

                    for (Map.Entry<ItemStack, Double> entry : prices.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null &&
                                enchantedBook != null && entry.getKey().isSimilar(enchantedBook)) {
                            enchantBonus += entry.getValue();
                            break;
                        }
                    }
                } catch (Exception e) {
                    Bukkit.getLogger().warning("Error calculating enchantment price: " + e.getMessage());
                }
            }
        }

        return basePrice == -1.0 ? -1.0 : basePrice + enchantBonus;
    }

    public void removePrice(ItemStack item) {
        if (item == null) return;

        prices.entrySet().removeIf(entry -> entry.getKey() != null && item != null && entry.getKey().isSimilar(item));

        String keyToRemove = null;
        for (Map.Entry<String, ItemPrice> entry : itemPrices.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getItem() != null &&
                    item != null && entry.getValue().getItem().isSimilar(item)) {
                keyToRemove = entry.getKey();
                break;
            }
        }

        if (keyToRemove != null) {
            itemPrices.remove(keyToRemove);

            final String finalKeyToRemove = keyToRemove;
            OScheduler.sync().run(() -> {
                List<String> pricesList = ConfigSelector.PRICESV2.getConfig().getStringList("prices");
                pricesList.remove(finalKeyToRemove);
                ConfigSelector.PRICESV2.getConfig().set("prices", pricesList);
                ConfigSelector.PRICESV2.getConfig().set(finalKeyToRemove, null);
                ConfigSelector.PRICESV2.saveConfig();

                updateConfig();
            });
        }
    }

    public void addOrUpdatePrice(ItemStack item, double amount) {
        if (item == null) return;

        if (amount == -1) {
            removePrice(item);
            return;
        }

        ItemStack existingItem = null;
        for (ItemStack key : prices.keySet()) {
            if (key != null && item != null && key.isSimilar(item)) {
                existingItem = key;
                break;
            }
        }

        if (existingItem != null) {
            prices.put(existingItem, amount);
        } else {
            prices.put(item, amount);
        }

        String existingKey = null;
        for (Map.Entry<String, ItemPrice> entry : itemPrices.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getItem() != null &&
                    item != null && entry.getValue().getItem().isSimilar(item)) {
                existingKey = entry.getKey();
                break;
            }
        }

        if (existingKey != null) {
            ItemPrice existingPrice = itemPrices.get(existingKey);
            if (existingPrice != null) {
                existingPrice.setPrice(amount);
                pendingSaves.add(existingKey);
            }
        } else {
            String newKey = "item_" + findNextKeyIndex();
            ItemPrice newItem = new ItemPrice(item, amount, newKey);
            itemPrices.put(newKey, newItem);
            pendingSaves.add(newKey);
        }

        scheduleBatchSave();
    }

    private void updateConfig() {
        if (!Bukkit.isPrimaryThread()) {
            OScheduler.sync().run(this::updateConfig);
            return;
        }

        try {
            List<String> priceList = new ArrayList<>();
            ConfigurationSection itemsSection = ConfigSelector.PRICES.getConfig().createSection("items");
            int index = 0;
            for (Map.Entry<ItemStack, Double> entry : prices.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null) continue;

                String key = "item_" + index;
                itemsSection.set(key, entry.getKey().serialize());
                priceList.add(key + ":" + entry.getValue());
                index++;
            }
            ConfigSelector.PRICES.getConfig().set("prices", priceList);
            ConfigSelector.PRICES.saveConfig();
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error in updateConfig: " + e.getMessage());
        }
    }

    public ItemPrice getItemPrice(String key) {
        if (key == null) return null;
        return itemPrices.get(key);
    }

    public ItemPrice getItemPrice(ItemStack item) {
        if (item == null) return null;

/*        for (ItemPrice itemPrice : itemPrices.values()) {
            if (itemPrice != null && itemPrice.getItem() != null &&
                    item != null && itemPrice.getItem().isSimilar(item)) {
                return itemPrice;
            }
        }

        return null;*/
        return itemLink.get(item);
    }

    public List<String> getAliases(String key) {
        if (key == null) return new ArrayList<>();

        ItemPrice itemPrice = itemPrices.get(key);
        return itemPrice != null ? itemPrice.getAliases() : new ArrayList<>();
    }

    public String getCategory(String key) {
        if (key == null) return "UNSET";

        ItemPrice itemPrice = itemPrices.get(key);
        return itemPrice != null ? itemPrice.getCategory() : "UNSET";
    }

    public void updateAliases(String key, List<String> aliases) {
        if (key == null || aliases == null) return;

        ItemPrice itemPrice = itemPrices.get(key);
        if (itemPrice != null) {
            itemPrice.setAliases(aliases);
            pendingSaves.add(key);
            scheduleBatchSave();
        }
    }

    public void updateCategory(String key, String category) {
        if (key == null || category == null) return;

        ItemPrice itemPrice = itemPrices.get(key);
        if (itemPrice != null) {
            itemPrice.setCategory(category);
            pendingSaves.add(key);
            scheduleBatchSave();
        }
    }

    private void addOrUpdateItemPrice(ItemPrice itemPrice) {
        if (itemPrice == null || itemPrice.getItem() == null || itemPrice.getItemKey() == null) return;

        itemPrices.put(itemPrice.getItemKey(), itemPrice);
        prices.put(itemPrice.getItem(), itemPrice.getPrice());

        pendingSaves.add(itemPrice.getItemKey());
        scheduleBatchSave();
    }

    public String addItem(ItemStack item, double price) {
        if (item == null) return null;

        for (Map.Entry<String, ItemPrice> entry : itemPrices.entrySet()) {
            if (entry.getValue() != null && entry.getValue().getItem() != null &&
                    entry.getValue().getItem().isSimilar(item)) {
                entry.getValue().setPrice(price);
                prices.put(entry.getValue().getItem(), price);
                return entry.getKey();
            }
        }

        String newKey = "item_" + findNextKeyIndex();
        ItemPrice itemPrice = new ItemPrice(item, price, newKey);
        addOrUpdateItemPrice(itemPrice);
        return newKey;
    }

    public void addItemWithKey(ItemStack item, double price, String customKey) {
        if (item == null || customKey == null || itemPrices.containsKey(customKey)) {
            Bukkit.getLogger().warning("Cannot add item with key " + customKey + " as it already exists or parameters are invalid");
            return;
        }

        ItemPrice itemPrice = new ItemPrice(item, price, customKey);
        addOrUpdateItemPrice(itemPrice);
    }

    private int findNextKeyIndex() {
        int maxIndex = -1;
        for (String key : itemPrices.keySet()) {
            if (key != null && key.startsWith("item_")) {
                try {
                    int index = Integer.parseInt(key.substring(5));
                    maxIndex = Math.max(maxIndex, index);
                } catch (NumberFormatException e) {}
            }
        }
        return maxIndex + 1;
    }

    @NotNull
    public static ItemStack deserialize(@NotNull Map<String, Object> args) {
        try {
            if (args.containsKey("id") && !args.containsKey("type")) {
                ItemStack newFormat = deserializeNewFormat(args);
                if (newFormat != null && newFormat.getType() != Material.AIR)
                    return newFormat;
            }

            int version = args.containsKey("v") ? ((Number)args.get("v")).intValue() : -1;
            short damage = 0;
            int amount = 1;
            if (args.containsKey("damage")) {
                damage = ((Number)args.get("damage")).shortValue();
            }

            Material type;
            if (version < 0) {
                type = XUtil.crossMaterial("LEGACY_" + (String)args.get("type"));
                byte dataVal = type != null && type.getMaxDurability() == 0 ? (byte)damage : 0;
                type = Bukkit.getUnsafe().fromLegacy(new MaterialData(type, dataVal), true);
                if (dataVal != 0) {
                    damage = 0;
                }
            } else {
                try {
                    type = Bukkit.getUnsafe().getMaterial((String)args.get("type"), version);
                } catch (Exception e) {
                    type = XUtil.crossMaterial((String) args.get("type"));
                }
            }

            if (type == null) {
                Bukkit.getLogger().warning("Could not deserialize material type");
                return new ItemStack(Material.AIR);
            }

            if (args.containsKey("amount")) {
                amount = ((Number)args.get("amount")).intValue();
            }

            ItemStack result = new ItemStack(type, amount);
            Object raw;
            if (args.containsKey("enchantments")) {
                raw = args.get("enchantments");
                if (raw instanceof Map) {
                    Map<?, ?> map = (Map)raw;
                    Iterator var9 = map.entrySet().iterator();

                    while(var9.hasNext()) {
                        Map.Entry<?, ?> entry = (Map.Entry)var9.next();
                        Enchantment enchantment = Enchantment.getByName(entry.getKey().toString());
                        if (enchantment != null && entry.getValue() instanceof Integer) {
                            result.addUnsafeEnchantment(enchantment, (Integer)entry.getValue());
                        }
                    }
                }
            }

            if (args.containsKey("meta")) {
                raw = args.get("meta");
                if (raw instanceof ItemMeta) {
                    ((ItemMeta) raw).setVersion(version);
                    result.setItemMeta((ItemMeta) raw);
                } else if (raw instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> metaMap = convertSectionsToMaps((Map<String, Object>) raw);
                    ConfigurationSerializable metaObj = ConfigurationSerialization.deserializeObject(metaMap);
                    if (metaObj instanceof ItemMeta) {
                        ((ItemMeta) metaObj).setVersion(version);
                        result.setItemMeta((ItemMeta) metaObj);
                    }
                } else if (raw instanceof ConfigurationSection) {
                    Map<String, Object> metaMap = convertSectionsToMaps(((ConfigurationSection) raw).getValues(false));
                    ConfigurationSerializable metaObj = ConfigurationSerialization.deserializeObject(metaMap);
                    if (metaObj instanceof ItemMeta) {
                        ((ItemMeta) metaObj).setVersion(version);
                        result.setItemMeta((ItemMeta) metaObj);
                    }
                }
            }

            if (version < 0 && args.containsKey("damage")) {
                result.setDurability(damage);
            }

            return result;
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error deserializing item: " + e.getMessage());
            return new ItemStack(Material.AIR);
        }
    }

    @SuppressWarnings("unchecked")
    private static ItemStack deserializeNewFormat(Map<String, Object> args) {
        try {
            Object idObj = args.get("id");
            if (!(idObj instanceof String idString)) return null;

            String materialName = idString;
            int colon = materialName.indexOf(':');
            if (colon >= 0)
                materialName = materialName.substring(colon + 1);

            Material material = XUtil.crossMaterial(materialName.toUpperCase());
            if (material == null) {
                Bukkit.getLogger().warning("Could not resolve material from new format: " + idString);
                return null;
            }

            int count = 1;
            Object countObj = args.get("count");
            if (countObj instanceof Number)
                count = ((Number) countObj).intValue();

            return new ItemStack(material, count);
        } catch (Exception e) {
            Bukkit.getLogger().warning("Failed new-format deserialization: " + e.getMessage());
            return null;
        }
    }

    private static Map<String, Object> convertSectionsToMaps(Map<String, Object> map) {
        Map<String, Object> result = new LinkedHashMap<>(map);
        for (Map.Entry<String, Object> entry : result.entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) {
                entry.setValue(convertSectionsToMaps(((ConfigurationSection) entry.getValue()).getValues(false)));
            } else if (entry.getValue() instanceof Map) {
                entry.setValue(convertSectionsToMaps((Map<String, Object>) entry.getValue()));
            }
        }
        return result;
    }
}
