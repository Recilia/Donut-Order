package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.commons.objectholders.BulletItem;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.commons.util.sound.SoundWrapper;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ItemManager {

    @Getter(lazy = true)
    private static final ItemManager instance = new ItemManager();

    private final Map<String, ConfigItems> configItemsMap = new HashMap<>();

    @Getter
    public static class ConfigItems {
        private final Map<String, NormalItem> normalItems = new HashMap<>();
        private final Map<String, BulletItem> bulletItems = new HashMap<>();
    }

    public void load() {
        loadConfigItems("orders", ConfigSelector.ORDERS.getConfig());
        loadConfigItems("new_order", ConfigSelector.NEW_ORDER.getConfig());
        loadConfigItems("select_item", ConfigSelector.SELECT_ITEM.getConfig());
        loadConfigItems("your_orders", ConfigSelector.YOUR_ORDERS.getConfig());
        loadConfigItems("edit_order", ConfigSelector.EDIT_ORDER.getConfig());
        loadConfigItems("edit_order_confirm", ConfigSelector.EDIT_ORDER_CONFIRM.getConfig());
        loadConfigItems("pick_enchantments", ConfigSelector.PICK_ENCHANTMENTS.getConfig());
        loadConfigItems("collect_items", ConfigSelector.COLLECT_ITEMS.getConfig());
        loadConfigItems("order_delivery_confirm", ConfigSelector.ORDER_DELIVERY_CONFIRM.getConfig());
    }

    private void loadConfigItems(String configKey, ConfigurationSection config) {
        ConfigItems items = new ConfigItems();
        loadItems(items, config.getConfigurationSection("items"));
        configItemsMap.put(configKey, items);
    }

    private void loadItems(ConfigItems items, ConfigurationSection config) {
        if (config == null) return;

        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            if (section == null) {
                System.out.println("[DonutOrder] Warning: Section '" + key + "' is null!");
                continue;
            }

            try {
                if (section.contains("options")) {
                    Class<? extends Enum<?>> enumClass = switch (key) {
                        case "sort" -> SortOrders.class;
                        case "filter" -> Filter.class;
                        case "sort_select" -> SortSelectItem.class;
                        default -> null;
                    };

                    SoundWrapper sound = SoundManager.getInstance()
                            .getSound(section.getString("sound", "1111"));

                    try {
                        items.getBulletItems().put(key, new BulletItem(
                                section.getString("name"),
                                XUtil.crossMaterial(section.getString("material")),
                                section.getInt("customModelData", -1),
                                section.getInt("slot", -1),
                                section.getString("selected_color"),
                                section.getString("unselected_color"),
                                section.getString("bullet_icon"),
                                section.getStringList("options"),
                                enumClass,
                                sound
                        ));
                    } catch (Exception e) {
                        System.out.println("[DonutOrder] Warning: Error loading bullet item '" + key + "': " + e.getMessage());
                    }
                } else {
                    String name = section.getString("name");
                    String material = section.getString("material");
                    List<String> lore = section.getStringList("lore");
                    int customModelData = section.getInt("customModelData", -1);

                    List<Integer> slots;
                    if (section.isList("slot")) {
                        slots = section.getIntegerList("slot");
                    } else {
                        int slot = section.getInt("slot", -1);
                        slots = slot == -1 ? List.of() : List.of(slot);
                    }

                    SoundWrapper sound = SoundManager.getInstance()
                            .getSound(section.getString("sound", "1111"));

                    if (name == null || material == null) {
                        System.out.println("[DonutOrder] Warning: Missing required fields for normal item '" + key + "'!");
                        continue;
                    }

                    try {
                        Material mat = XUtil.crossMaterial(material);
                        items.getNormalItems().put(key, new NormalItem(
                                name,
                                lore,
                                mat,
                                customModelData,
                                slots,
                                sound
                        ));
                    } catch (IllegalArgumentException e) {
                        System.out.println("[DonutOrder] Warning: Invalid material '" + material + "' for item '" + key + "'!");
                    }
                }
            } catch (Exception e) {
                System.out.println("[DonutOrder] Warning: Error loading item '" + key + "': " + e.getMessage());
            }
        }
    }

    public NormalItem getNormalItem(ConfigType config, String itemKey) {
        ConfigItems items = configItemsMap.get(config.getKey());
        return items != null ? items.getNormalItems().get(itemKey) : null;
    }

    public BulletItem getBulletItem(ConfigType config, String itemKey) {
        ConfigItems items = configItemsMap.get(config.getKey());
        return items != null ? items.getBulletItems().get(itemKey) : null;
    }
}
