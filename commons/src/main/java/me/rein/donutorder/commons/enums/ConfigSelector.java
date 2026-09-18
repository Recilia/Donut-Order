package me.rein.donutorder.commons.enums;

import me.rein.donutorder.commons.Commons;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public enum ConfigSelector {
    CONFIG("config.yml"),
    MESSAGES("messages.yml"),
    HOOKS("hooks.yml"),
    PRICES("prices.yml"),
    PRICESV2("pricesv2.yml"),
    DATABASES("databases.yml"),
    BLACKLIST("blacklist.yml"),
    SOUNDS("sounds.yml"),

    /*
     * GUI
     */
    ORDERS("gui/orders.yml"),
    NEW_ORDER("gui/new_order.yml"),
    SELECT_ITEM("gui/select_item.yml"),
    YOUR_ORDERS("gui/your_orders.yml"),
    EDIT_ORDER("gui/edit_order.yml"),
    EDIT_ORDER_CONFIRM("gui/edit_order_confirm.yml"),
    PICK_ENCHANTMENTS("gui/pick_enchantments.yml"),
    ORDER_DELIVERY("gui/order_delivery.yml"),
    COLLECT_ITEMS("gui/collect_items.yml"),
    ORDER_DELIVERY_CONFIRM("gui/order_delivery_confirm.yml")
    ;

    private final String fileName;
    private File file;
    private FileConfiguration config;

    ConfigSelector(String fileName) {
        this.fileName = fileName;
    }

    public void initialize() {
        file = new File(Commons.getPlugin().getDataFolder(), fileName);
        if (!file.exists()) {
            file.getParentFile().mkdirs();
            Commons.getPlugin().saveResource(fileName, false);
        }
        config = YamlConfiguration.loadConfiguration(file);
    }

    public FileConfiguration getConfig() {
        if (config == null) {
            throw new IllegalStateException("Config " + fileName + " has not been initialized. Call initialize() first.");
        }

        return config;
    }

    public void saveConfig() {
        if (config == null || file == null) {
            throw new IllegalStateException("Config " + fileName + " has not been initialized. Call initialize() first.");
        }
        try {
            getConfig().save(file);
//            getConfig().load(file);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void reload() {
        if (file == null) {
            throw new IllegalStateException("Config " + fileName + " has not been initialized. Call initialize() first.");
        }
        config = YamlConfiguration.loadConfiguration(file);
//        try {
//            config.save(file);
//            config.load(file);
//        } catch (IOException | InvalidConfigurationException e) {
//            throw new RuntimeException(e);
//        }
    }
}
