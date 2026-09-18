package me.rein.donutorder.inventory.impl;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.exception.SignGUIVersionException;
import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.MaterialManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.BulletItem;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.message.NameUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SelectItemGUI extends InventoryGUI {
    private final ItemBuilder currentItemBuilder;
    private final Filter filter;
    private final SortSelectItem sort;
    private final String searchTerm;
    private final int currentPage;
    private final int maxPage;
    private final List<Integer> itemSlots;

    public SelectItemGUI(ItemBuilder currentItemBuilder, Filter filter, SortSelectItem sort, String search, int currentPage) {
        this(currentItemBuilder, filter, sort, search, currentPage, loadItemSlots());
    }

    public SelectItemGUI(ItemBuilder currentItemBuilder, Filter filter, SortSelectItem sort, String search, int currentPage, List<Integer> itemSlots) {
        super(ConfigSelector.SELECT_ITEM.getConfig().getString("title"));
        this.currentItemBuilder = currentItemBuilder;
        this.filter = filter;
        this.sort = sort;
        this.searchTerm = search;
        this.currentPage = currentPage;
        this.itemSlots = itemSlots;
        this.maxPage = MaterialManager.getInstance().getMaxPages(filter, search, null, itemSlots.size());
    }

    private static List<Integer> loadItemSlots() {
        List<Integer> slots = ConfigSelector.SELECT_ITEM.getConfig().getIntegerList("itemSlots");

        if (slots.isEmpty()) {
            slots = new ArrayList<>();
            for (int i = 0; i < 45; i++)
                slots.add(i);
        }

        return slots;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, super.title);
    }

    @Override
    public void decorate(Player player) {
        List<ItemStack> pageMaterials = MaterialManager.getInstance()
                .getPageMaterials(currentPage, filter, sort, searchTerm, null, itemSlots.size());

        for (int i = 0; i < pageMaterials.size() && i < itemSlots.size(); i++) {
            ItemStack materialItem = pageMaterials.get(i);
            final int slot = itemSlots.get(i);
            this.addButton(slot, new InventoryButton()
                    .async(true)
                    .creator(p -> {
                        ItemBuilder builder = new ItemBuilder(materialItem);

                        if (builder.getItemMeta() != null && builder.getItemMeta().hasDisplayName()) {
                            builder.appendLore(player, "", Messages.SELECT_ITEM.getMessage());

                            if (currentItemBuilder != null) {
                                if(currentItemBuilder.getItemMeta().hasCustomModelData()) {
                                    builder.customModelData(currentItemBuilder.getItemMeta().getCustomModelData());
                                }
                            }
                        } else {
                            if (!ValuesManager.getInstance().isLocalization()) {
                                // dirty workaround :(
                                builder.name("&f"+ NameUtil.formatMaterialName(
                                        materialItem.getType().name(), materialItem.getType().name()))
                                        .lore(player, Messages.SELECT_ITEM.getMessage());
                            } else {
                                // item localization
                                builder
                                        .lore(player, Messages.SELECT_ITEM.getMessage());
                            }
                        }

                        return builder;
                    })
                    .consumer(event -> {
                        ItemBuilder finalBuilder = new ItemBuilder(materialItem);
                        if (MaterialManager.getInstance().isEnchantable(materialItem) &&
                        !ValuesManager.getInstance().isSkipEnchants()) {
                            DonutOrder.getGUIManager().openGUI(new PickEnchantmentsGUI(
                                    finalBuilder, 1
                            ), player);
                        } else {
                            DonutOrder.getGUIManager().openGUI(new NewOrderGUI(
                                    1, 0, true, finalBuilder
                            ), player);
                        }
                    }));
        }

        NormalItem backItem = ItemManager.getInstance().getNormalItem(ConfigType.SELECT_ITEM,"back");
        this.addButton(backItem.getSlot(), new InventoryButton()
                .sound(backItem.getSound())
                .creator(p -> backItem.getItem())
                .consumer(event -> {
                    if (currentPage > 1) {
                        DonutOrder.getGUIManager().openGUI(new SelectItemGUI(currentItemBuilder,
                                filter, sort, searchTerm, currentPage - 1, itemSlots), player);
                    }
                }));

        NormalItem nextItem = ItemManager.getInstance().getNormalItem(ConfigType.SELECT_ITEM,"next");
        this.addButton(nextItem.getSlot(), new InventoryButton()
                .sound(nextItem.getSound())
                .creator(p -> nextItem.getItem())
                .consumer(event -> {
                    if (currentPage < maxPage) {
                        DonutOrder.getGUIManager().openGUI(new SelectItemGUI(currentItemBuilder,
                                filter, sort, searchTerm, currentPage + 1, itemSlots), player);
                    }
                }));

        NormalItem search = ItemManager.getInstance().getNormalItem(ConfigType.SELECT_ITEM,"search");
        this.addButton(search.getSlot(), new InventoryButton()
                .sound(search.getSound())
                .creator(p -> search.getItem())
                .consumer(event -> {
                    player.closeInventory();

                    try {
                        SignGUI.builder()
                                .setLines(Messages.SIGN_SEARCH.getLine(0),
                                        Messages.SIGN_SEARCH.getLine(1),
                                        Messages.SIGN_SEARCH.getLine(2),
                                        Messages.SIGN_SEARCH.getLine(3))
                                .setHandler((p, result) -> {
                                    String searchedTerm = result.getLine(0).trim();

                                    OScheduler.sync().runAtEntity(() -> {
                                        DonutOrder.getGUIManager().openGUI(
                                                new SelectItemGUI(currentItemBuilder, filter, sort, searchedTerm, 1, itemSlots),
                                                player
                                        );
                                    }, player);

                                    return Collections.emptyList();
                                })
                                .build()
                                .open(player);
                    } catch (SignGUIVersionException e) {}
                }));

        BulletItem sortItem = ItemManager.getInstance().getBulletItem(ConfigType.SELECT_ITEM,"sort_select");
        this.addButton(sortItem.getSlot(), new InventoryButton()
                .sound(sortItem.getSound())
                .creator(p -> sortItem.getItem(sort))
                .consumer(event -> {
                    SortSelectItem nextSort = (SortSelectItem) sortItem.getNextEnumOption(sort);
                    DonutOrder.getGUIManager().openGUI(new SelectItemGUI(
                            currentItemBuilder,
                            filter,
                            nextSort,
                            searchTerm,
                            1,
                            itemSlots
                    ), player);
                }));

        BulletItem filterItem = ItemManager.getInstance().getBulletItem(ConfigType.SELECT_ITEM,"filter");
        this.addButton(filterItem.getSlot(), new InventoryButton()
                .sound(filterItem.getSound())
                .creator(p -> filterItem.getItem(filter))
                .consumer(event -> {
                    Filter nextFilter = (Filter) filterItem.getNextEnumOption(filter);
                    DonutOrder.getGUIManager().openGUI(new SelectItemGUI(
                            currentItemBuilder,
                            nextFilter,
                            sort,
                            searchTerm,
                            1,
                            itemSlots
                    ), player);
                }));

        fillDecorationsFromConfig(ConfigType.SELECT_ITEM);

        super.decorate(player);
    }
}
