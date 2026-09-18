package me.rein.donutorder.api.shop;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.manager.MaterialManager;
import me.rein.donutorder.commons.manager.price.PriceManager;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ShopManager {

    @Getter(lazy = true)
    private static final ShopManager instance = new ShopManager();

    private final Map<String, ShopHook> registeredShops = new HashMap<>();
    private ShopHook activeShop = null;

    /**
     * Register a new shop integration
     * @param shopHook The shop hook to register
     */
    public void registerShop(
            ShopHook shopHook
    ) {
        registeredShops.put(shopHook.getName().toLowerCase(), shopHook);
    }

    /**
     * Set active shop from config
     * @param shopName Shop name from config
     * @return true if shop was set successfully
     */
    public boolean setActiveShop(
            String shopName
    ) {
        if (shopName.equalsIgnoreCase("none")) {
            activeShop = null;
            return true;
        }

        ShopHook shop = registeredShops.get(shopName.toLowerCase());

        if (shop == null)
            return false;

        activeShop = shop;
        shop.init();
        return true;
    }

    /**
     * Sync prices from active shop to all spawner configurations
     */
    public void syncPrices() {
        if (activeShop == null)
            return;

        Map<ItemStack, Double> itemStackWithPrices = new HashMap<>();

        PriceManager.getInstance().getPrices().forEach(
                (itemStack, price) -> {
            if (price == -1)
                return;

            itemStackWithPrices.put(itemStack, price);
        });

        MaterialManager.getInstance().initializeMaterials(itemStackWithPrices);
    }
}
