package me.rein.donutorder.commons.manager.price;

import lombok.Getter;
import lombok.Setter;
import me.rein.donutorder.commons.util.helper.MMOItemsHelper;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
public class ItemPrice {
    private ItemStack item;
    private double price;
    private String itemKey;
    private List<String> aliases;
    private String category;

    // MMOItems Support
    private final String mmoItemType;
    private final String mmoItemId;

    public ItemPrice(ItemStack item, double price, String itemKey) {
        this.item = item;
        this.price = price;
        this.itemKey = itemKey;
        this.aliases = new ArrayList<>();
        this.category = "UNSET";

        if (MMOItemsHelper.isMMOItem(item)) {
            this.mmoItemType = MMOItemsHelper.getMMOItemType(item);
            this.mmoItemId = MMOItemsHelper.getMMOItemId(item);
        } else {
            this.mmoItemType = null;
            this.mmoItemId = null;
        }
    }

    public ItemPrice(ItemStack item, double price, String itemKey, List<String> aliases, String category) {
        this.item = item;
        this.price = price;
        this.itemKey = itemKey;
        this.aliases = aliases;
        this.category = category;

        if (MMOItemsHelper.isMMOItem(item)) {
            this.mmoItemType = MMOItemsHelper.getMMOItemType(item);
            this.mmoItemId = MMOItemsHelper.getMMOItemId(item);
        } else {
            this.mmoItemType = null;
            this.mmoItemId = null;
        }
    }

    public boolean isMMOItem() {
        return mmoItemType != null && mmoItemId != null;
    }
}
