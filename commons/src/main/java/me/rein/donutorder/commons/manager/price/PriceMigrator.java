package me.rein.donutorder.commons.manager.price;

import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.manager.ValuesManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class PriceMigrator {

    public static void migrateToV2() {
        Bukkit.getLogger().info("Starting migration to PricesV2 format...");

        List<String> oldPricesList = ConfigSelector.PRICES.getConfig().getStringList("prices");
        ConfigurationSection oldItemsSection = ConfigSelector.PRICES.getConfig().getConfigurationSection("items");

        if (oldItemsSection == null) {
            Bukkit.getLogger().warning("No items section found in old prices.yml. Nothing to migrate.");
            return;
        }

        if (ValuesManager.getInstance().isStopAutoMigrationOnStart()) {
            return;
        }

        List<String> newPricesList = new ArrayList<>();

        for (String priceEntry : oldPricesList) {
            try {
                String[] arr = priceEntry.split(":");
                if (arr.length != 2) {
                    Bukkit.getLogger().warning("Invalid price entry: " + priceEntry);
                    continue;
                }

                String itemKey = arr[0];
                double price = Double.parseDouble(arr[1]);

                ConfigurationSection itemSection = oldItemsSection.getConfigurationSection(itemKey);
                if (itemSection == null) {
                    Bukkit.getLogger().warning("No item section found for: " + itemKey);
                    continue;
                }

                ConfigurationSection newItemSection = ConfigSelector.PRICESV2.getConfig().createSection(itemKey);

                ConfigurationSection newItemDataSection = newItemSection.createSection("item");
                for (String key : itemSection.getKeys(false)) {
                    newItemDataSection.set(key, itemSection.get(key));
                }

                newItemSection.set("price", price);
                newItemSection.set("aliases", new ArrayList<String>());
                newItemSection.set("category", "UNSET");

                newPricesList.add(itemKey);

            } catch (Exception e) {
                Bukkit.getLogger().severe("Error migrating price entry: " + priceEntry);
                e.printStackTrace();
            }
        }

        ConfigSelector.PRICESV2.getConfig().set("prices", newPricesList);
        ConfigSelector.PRICESV2.saveConfig();

        Bukkit.getLogger().info("Successfully migrated " + newPricesList.size() + " items to pricesv2.yml");
    }
}
