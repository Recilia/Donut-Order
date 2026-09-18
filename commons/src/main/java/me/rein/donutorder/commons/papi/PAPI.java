package me.rein.donutorder.commons.papi;

import me.clip.placeholderapi.PlaceholderAPI;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.papi.placeholders.DonutOrderPlaceholder;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;

public class PAPI {
    public static boolean isPAPIEnabled() {
        return Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI");
    }

    public static void registerPlaceholders() {
        if (!isPAPIEnabled())
            return;

        new DonutOrderPlaceholder().register();
    }

    public static List<String> setPlaceholders(OfflinePlayer offlinePlayer, List<String> lore) {
        if (!ValuesManager.getInstance().isFullLorePAPISupport())
            return lore;

        if (!isPAPIEnabled())
            return lore;

        if (offlinePlayer == null)
            return lore;

        return PlaceholderAPI.setPlaceholders(offlinePlayer, lore);
    }
}
