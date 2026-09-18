package me.rein.donutorder.commons.util.helper;


import io.lumine.mythic.lib.api.item.NBTItem;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;

public class MMOItemsHelper {

    private static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("MMOItems") != null;
    }

    public static boolean isMMOItem(ItemStack item) {
        if (!isAvailable() || item == null) return false;

        NBTItem nbt = NBTItem.get(item);
        return nbt.hasTag("MMOITEMS_ITEM_ID") && nbt.getString("MMOITEMS_ITEM_ID") != null;
    }

    public static String getMMOItemType(ItemStack item) {
        if (!isAvailable() || !isMMOItem(item)) return null;

        try {
            return net.Indyuce.mmoitems.MMOItems.getTypeName(item);
        } catch (Exception e) {
            return null;
        }
    }

    public static String getMMOItemId(ItemStack item) {
        if (!isAvailable() || !isMMOItem(item)) return null;

        try {
            return net.Indyuce.mmoitems.MMOItems.getID(item);
        } catch (Exception e) {
            return null;
        }
    }

    public static ItemStack getMMOItem(String typeName, String id) {
        if (!isAvailable() || typeName == null || id == null) return null;

        try {
            net.Indyuce.mmoitems.api.Type type = net.Indyuce.mmoitems.MMOItems.plugin.getTypes().get(typeName);
            if (type == null) return null;

            return net.Indyuce.mmoitems.MMOItems.plugin.getItem(type, id);
        } catch (Exception e) {
            return null;
        }
    }
}
