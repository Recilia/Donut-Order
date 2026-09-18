package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortSelectItem;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class PickEnchantmentsGUI extends InventoryGUI {
    private final int[] aura = {1, 9, 10};
    private static final int STARTING_SLOT = 3;
    private static final int SLOTS_PER_ROW = 9;

    private List<Enchantment> possibleEnchants;
    private final ItemBuilder item;
    private final int currentPage;
    private final int maxPage;

    public PickEnchantmentsGUI(ItemBuilder item, int currentPage) {
        super(ConfigSelector.PICK_ENCHANTMENTS.getConfig().getString("title"));
        this.item = item.clearLore();
        this.currentPage = currentPage;
        this.possibleEnchants = getPossibleEnchantments();
        this.maxPage = (int) Math.ceil((double) possibleEnchants.size() / 5);
    }

    private List<Enchantment> getPossibleEnchantments() {
        List<Enchantment> enchants = new ArrayList<>();

        for (Enchantment ench : Enchantment.values()) {
            if (ench == Enchantment.MENDING || ench == Enchantment.DURABILITY) {
                continue;
            }

            if (ench.canEnchantItem(item)) {
                enchants.add(ench);
            }
        }

        enchants.sort((e1, e2) -> {
            return Integer.compare(e2.getMaxLevel(), e1.getMaxLevel());
        });

        return enchants;
    }


    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, super.title);
    }

    @Override
    public void decorate(Player player) {
        int enchantsPerPage = 5; // 5 rows per page
        int startIndex = (currentPage - 1) * enchantsPerPage;
        int endIndex = Math.min(startIndex + enchantsPerPage, possibleEnchants.size());

        // Add each enchantment row
        for (int i = startIndex; i < endIndex; i++) {
            Enchantment enchant = possibleEnchants.get(i);
            int rowStart = STARTING_SLOT + ((i - startIndex) * SLOTS_PER_ROW);
            addEnchantmentRow(rowStart, enchant);
        }

        addEnchantmentBook(18, Enchantment.MENDING, 1);
        addEnchantmentBook(19, Enchantment.DURABILITY, 1);
        addEnchantmentBook(27, Enchantment.DURABILITY, 2);
        addEnchantmentBook(36, Enchantment.DURABILITY, 3);

        NormalItem back = ItemManager.getInstance().getNormalItem(ConfigType.PICK_ENCHANTMENTS, "back");
        this.addButton(back.getSlot(), new InventoryButton()
                .sound(back.getSound())
                .creator(p -> back.getItem())
                .consumer(event -> {
                    if (currentPage > 1) {
                        DonutOrder.getGUIManager().openGUI(
                                new PickEnchantmentsGUI(item, currentPage - 1), player);
                    }
                }));

        NormalItem next = ItemManager.getInstance().getNormalItem(ConfigType.PICK_ENCHANTMENTS, "next");
        this.addButton(next.getSlot(), new InventoryButton()
                .sound(next.getSound())
                .creator(p -> next.getItem())
                .consumer(event -> {
                    if (currentPage < maxPage) {
                        DonutOrder.getGUIManager().openGUI(
                                new PickEnchantmentsGUI(item, currentPage + 1), player);
                    }
                }));

        NormalItem confirm = ItemManager.getInstance().getNormalItem(ConfigType.PICK_ENCHANTMENTS, "confirm");
        this.addButton(confirm.getSlot(), new InventoryButton()
                .sound(confirm.getSound())
                .creator(p -> confirm.getItem())
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new NewOrderGUI(
                            1, 0, true, item
                    ), player);
                }));

        NormalItem cancel = ItemManager.getInstance().getNormalItem(ConfigType.PICK_ENCHANTMENTS, "cancel");
        this.addButton(cancel.getSlot(), new InventoryButton()
                .sound(cancel.getSound())
                .creator(p -> cancel.getItem())
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(
                            new SelectItemGUI(null, Filter.ALL, SortSelectItem.A_Z, "", 1)
                    ,player);
                }));

        for (int i : aura) {
            this.addButton(i, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("BLACK_STAINED_GLASS_PANE"))
                            .name(" "))
                    .consumer(event -> {}));
        }

        this.addButton(0, new InventoryButton()
                .creator(p -> item)
                .consumer(event -> {}));

        fillDecorationsFromConfig(ConfigType.PICK_ENCHANTMENTS);

        super.decorate(player);
    }

    private void addEnchantmentRow(int startingSlot, Enchantment enchantment) {
        int maxLevel = enchantment.getMaxLevel();

        // Add each level of the enchantment in the row
        for (int level = 1; level <= maxLevel; level++) {
            addEnchantmentBook(startingSlot + (level - 1), enchantment, level);
        }
    }

    private void addEnchantmentBook(int slot, Enchantment enchantment, int level) {
        ItemBuilder book = new ItemBuilder(XUtil.crossMaterial("ENCHANTED_BOOK"));
        EnchantmentStorageMeta meta = (EnchantmentStorageMeta) book.getItemMeta();
        meta.addStoredEnchant(enchantment, level, true);

        String enchName = formatEnchantmentName(enchantment, level);
        book.setItemMeta(meta);

        boolean isSelected = item.getEnchantments().containsKey(enchantment) &&
                item.getEnchantments().get(enchantment) == level;

        book.lore(null, isSelected ?
                Messages.PICK_ENCHANTMENTS_SELECTED.getMessage() :
                Messages.PICK_ENCHANTMENTS_UNSELECTED.getMessage());

        this.addButton(slot, new InventoryButton()
                .creator(p -> book)
                .consumer(event -> {
                    ItemMeta itemMeta = item.getItemMeta();
                    item.removeEnchantment(enchantment);
                    if (isSelected) {
                        item.removeEnchantment(enchantment);
                    } else {
                        if (itemMeta.hasEnchant(enchantment)) {
                            item.removeEnchantment(enchantment);
                        }
                        item.enchantment(enchantment, level);
                    }

                    DonutOrder.getGUIManager().openGUI(
                            new PickEnchantmentsGUI(item, currentPage),
                            (Player) event.getWhoClicked());
                }));
    }

    private String formatEnchantmentName(Enchantment enchantment, int level) {
        String name = enchantment.getKey().getKey()
                .replace("_", " ")
                .toLowerCase();

        name = Arrays.stream(name.split(" "))
                .map(word -> word.substring(0, 1).toUpperCase() + word.substring(1))
                .collect(Collectors.joining(" "));

        if (level > 1) {
            name += " " + toRoman(level);
        }

        return name;
    }

    private String toRoman(int number) {
        String[] romanNumerals = {"", "I", "II", "III", "IV", "V"};
        return number <= 5 ? romanNumerals[number] : String.valueOf(number);
    }
}
