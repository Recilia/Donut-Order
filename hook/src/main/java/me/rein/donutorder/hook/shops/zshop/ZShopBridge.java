package me.rein.donutorder.hook.shops.zshop;

import fr.maxlego08.zshop.ShopPlugin;
import fr.maxlego08.zshop.api.buttons.ItemButton;
import me.rein.donutorder.api.shop.ShopHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Optional;

public class ZShopBridge implements ShopHook {

    private ShopPlugin shopPlugin;

    @Override
    public String getName() {
        return "ZShop";
    }

    @Override
    public void init() {
        shopPlugin = new ShopPlugin();
    }

    @Override
    public double getSellPrice(
            ItemStack itemStack
    ) {
        Optional<ItemButton> shopItem = shopPlugin.getShopManager().getItemButton(itemStack.getType());

        if (shopItem.isEmpty())
            return -1;

        double sellPrice = shopItem.get().getSellPrice();
        return sellPrice > 0 ? sellPrice : -1;
    }

    @Override
    public double getSellPrice(
            Player player,
            ItemStack itemStack
    ) {
        return getSellPrice(itemStack);
    }
}
