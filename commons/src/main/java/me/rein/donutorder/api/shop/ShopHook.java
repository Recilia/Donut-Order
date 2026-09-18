package me.rein.donutorder.api.shop;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public interface ShopHook {

    String getName();
    void init();

    double getSellPrice(ItemStack itemStack);
    double getSellPrice(Player player, ItemStack itemStack);
}
