package me.rein.donutorder.hook.shops.shopguiplus;

import me.rein.donutorder.api.shop.ShopHook;
import net.brcdev.shopgui.ShopGuiPlusApi;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class ShopGuiPlusBridge implements ShopHook {

    @Override
    public String getName() {
        return "ShopGUI+";
    }

    @Override
    public void init() {}

    @Override
    public double getSellPrice(
            ItemStack itemStack
    ) {
        return ShopGuiPlusApi.getItemStackPriceSell(itemStack);
    }

    @Override
    public double getSellPrice(
            Player player,
            ItemStack itemStack
    ) {
        return ShopGuiPlusApi.getItemStackPriceSell(player, itemStack);
    }
}
