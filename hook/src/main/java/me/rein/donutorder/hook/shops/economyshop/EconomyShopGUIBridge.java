package me.rein.donutorder.hook.shops.economyshop;

import me.gypopo.economyshopgui.api.EconomyShopGUIHook;
import me.gypopo.economyshopgui.objects.ShopItem;
import me.rein.donutorder.api.shop.ShopHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class EconomyShopGUIBridge implements ShopHook {

    @Override
    public String getName() {
        return "EconomyShopGUI";
    }

    @Override
    public void init() {

    }

    @Override
    public double getSellPrice(
            ItemStack itemStack
    ) {
        ShopItem shopItem = EconomyShopGUIHook.getShopItem(itemStack);

        if (shopItem == null)
            return -1;

        double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, itemStack);
        return sellPrice > 0 ? sellPrice : -1;
    }

    @Override
    public double getSellPrice(
            Player player,
            ItemStack itemStack
    ) {
        ShopItem shopItem = EconomyShopGUIHook.getShopItem(player, itemStack);

        if (shopItem == null)
            return -1;

        double sellPrice = EconomyShopGUIHook.getItemSellPrice(shopItem, itemStack);
        return sellPrice > 0 ? sellPrice : -1;
    }
}
