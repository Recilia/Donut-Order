package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.manager.price.PriceManager;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class BlacklistManager {

    @Getter(lazy = true)
    private static final BlacklistManager instance = new BlacklistManager();

    private List<String> blacklistedItems = new ArrayList<>();

    public void load() {
        FileConfiguration config = ConfigSelector.BLACKLIST.getConfig();
        blacklistedItems = config.getStringList("blacklisted_items");
    }

    public void addItem(String item) {
        String upperItem = item.toUpperCase();
        if (!blacklistedItems.contains(upperItem)) {
            blacklistedItems.add(upperItem);
            saveBlacklist();
        }
        ItemStack removedItem = PriceManager.getInstance().getItemFromConfig(item);
        PriceManager.getInstance().getPrices().remove(removedItem);
    }

    public void removeItem(String item) {
        String upperItem = item.toUpperCase();
        if (blacklistedItems.contains(upperItem)) {
            blacklistedItems.remove(upperItem);
            saveBlacklist();
        }
        ItemStack addedItem = PriceManager.getInstance().getItemFromConfig(item);
        double price = PriceManager.getInstance().getPrice(addedItem);
        PriceManager.getInstance().getPrices().put(addedItem, price);
    }

    public boolean isBlacklisted(String item) {
        return blacklistedItems.contains(item.toUpperCase());
    }

    public List<String> getBlacklistedItems() {
        return new ArrayList<>(blacklistedItems);
    }

    private void saveBlacklist() {
        FileConfiguration config = ConfigSelector.BLACKLIST.getConfig();
        config.set("blacklisted_items", blacklistedItems);
        ConfigSelector.BLACKLIST.saveConfig();
    }
}
