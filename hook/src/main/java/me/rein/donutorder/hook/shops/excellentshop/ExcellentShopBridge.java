package me.rein.donutorder.hook.shops.excellentshop;

import me.rein.donutorder.api.shop.ShopHook;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.Method;

public class ExcellentShopBridge implements ShopHook {

    private Object module;
    private Method getBestProductForMethod;
    private Method getBestProductForWithPlayerMethod;
    private Method getPriceMethod;
    private Object tradeTypeSell;

    @Override
    public String getName() {
        return "ExcellentShop";
    }

    @Override
    public void init() {
        try {
            Class<?> shopAPIClass = Class.forName("su.nightexpress.nexshop.ShopAPI");
            Method getVirtualShopMethod = shopAPIClass.getMethod("getVirtualShop");
            module = getVirtualShopMethod.invoke(null);

            Class<?> virtualShopModuleClass = module.getClass();
            Class<?> tradeTypeClass = Class.forName("su.nightexpress.nexshop.api.shop.type.TradeType");
            tradeTypeSell = Enum.valueOf((Class<Enum>) tradeTypeClass, "SELL");

            getBestProductForMethod = virtualShopModuleClass.getMethod("getBestProductFor",
                    org.bukkit.inventory.ItemStack.class,
                    tradeTypeClass
            );

            getBestProductForWithPlayerMethod = virtualShopModuleClass.getMethod("getBestProductFor",
                    org.bukkit.inventory.ItemStack.class,
                    tradeTypeClass,
                    Player.class
            );

            Class<?> productClass = Class.forName("su.nightexpress.nexshop.api.shop.product.Product");
            getPriceMethod = productClass.getMethod("getPrice", tradeTypeClass);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public double getSellPrice(
            ItemStack itemStack
    ) {
        try {
            Object product = getBestProductForMethod.invoke(module, itemStack,
                    tradeTypeSell);
            if (product == null) {
                return 0.0;
            }

            return (double) getPriceMethod.invoke(product, tradeTypeSell);
        } catch (Exception e) {
            e.printStackTrace();
            return 0.0;
        }
    }

    @Override
    public double getSellPrice(
            Player player,
            ItemStack itemStack
    ) {
        return getSellPrice(itemStack);
    }
}
