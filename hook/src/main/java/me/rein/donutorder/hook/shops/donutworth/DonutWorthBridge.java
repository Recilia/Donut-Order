package me.rein.donutorder.hook.shops.donutworth;

import me.rein.donutorder.api.shop.ShopHook;
import me.rein.donutworth.api.util.Prices;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class DonutWorthBridge implements ShopHook {

    @Override
    public String getName() {
        return "DonutWorth";
    }

    @Override
    public void init() {

    }

    @Override
    public double getSellPrice(
            ItemStack itemStack
    ) {
//        double sellPrice = WorthManager.getInstance().getPrice(itemStack);
        double sellPrice = Prices.getPrice(itemStack);
        return sellPrice > 0 ? sellPrice : -1;
    }

    @Override
    public double getSellPrice(
            Player player,
            ItemStack itemStack
    ) {
        return Prices.getPriceWithMultiplier(player, itemStack);
    }
}
