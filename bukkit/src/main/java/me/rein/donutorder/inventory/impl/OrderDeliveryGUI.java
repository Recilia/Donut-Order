package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.inventory.DraggableInventoryGUI;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderDeliveryGUI extends DraggableInventoryGUI {
    private final AtomicBoolean isClosing = new AtomicBoolean(false);

    private final Order order;

    public OrderDeliveryGUI(
            Order order
    ) {
        super(ConfigSelector.ORDER_DELIVERY.getConfig().getString("title"), generateAllSlots());

        this.order = order;
    }

    public OrderDeliveryGUI(
            Order order,
            Map<Integer, ItemStack> items
    ) {
        super(ConfigSelector.ORDER_DELIVERY.getConfig().getString("title"), generateAllSlots());

        this.order = order;

        for (Map.Entry<Integer, ItemStack> item : items.entrySet()) {
            int slot = item.getKey();
            ItemStack itemStack = item.getValue();

            getInventory().setItem(slot, itemStack);
        }
    }

    private static List<Integer> generateAllSlots() {
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            slots.add(i);
        }
        return slots;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 36, title);
    }

    @Override
    protected void handleDraggedItems(Player player, Map<Integer, ItemStack> items) {
        if (items.isEmpty()) {
            return;
        }

        Order currentOrder = OrderManager.getInstance().getOrders().get(order.getOrderId());
        if (currentOrder == null) {
            player.sendMessage("§cThis order no longer exists. Returning your items.");
            returnAllItems(player, items);
            return;
        }

        Map<Integer, ItemStack> validItems = new HashMap<>();
        List<ItemStack> invalidItems = new ArrayList<>();

        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack draggedItem = entry.getValue();
            if (draggedItem == null) continue;

            boolean isValid = false;

            if (!isShulkerBox(draggedItem) || (order.getItem().getType() == XUtil.crossMaterial("SHULKER_BOX"))) {
                if (ItemUtil.hasSimilarMetadata(draggedItem, order.getItem())) {
                    isValid = true;
                }
            } else {
                if (checkShulkerContainsOrderItem(draggedItem, order.getItem())) {
                    isValid = true;
                }
            }

            if (isValid) {
                validItems.put(entry.getKey(), draggedItem);
            } else {
                invalidItems.add(draggedItem);
            }
        }

        for (ItemStack invalidItem : invalidItems) {
            if (invalidItem == null || invalidItem.getType() == Material.AIR) continue;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(invalidItem);
            if (!leftover.isEmpty()) {
                for (ItemStack dropItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                }
            }
        }

        if (!validItems.isEmpty()) {
            OScheduler.sync().runAtEntityLater(() -> {
                if (!player.isOnline()) {
                    returnAllItems(player, validItems);
                    return;
                }

                DonutOrder.getGUIManager().openGUI(new OrderDeliveryConfirmGUI(
                        order,
                        validItems
                ), player);
            }, player, 1L);
        }
    }

    private void returnAllItems(Player player, Map<Integer, ItemStack> items) {
        for (ItemStack item : items.values()) {
            if (item == null || item.getType() == Material.AIR) continue;

            if (player.isOnline()) {
                Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
                if (!leftover.isEmpty()) {
                    for (ItemStack dropItem : leftover.values()) {
                        player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                    }
                }
            } else {
                player.getWorld().dropItemNaturally(player.getLocation(), item);
            }
        }
    }

    private boolean checkShulkerContainsOrderItem(ItemStack shulkerItem, ItemStack orderItem) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return false;
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return false;
        }

        Inventory shulkerInventory = shulkerBox.getInventory();
        ItemStack[] contents = shulkerInventory.getContents();

        for (ItemStack content : contents) {
            if (content == null || content.getType() == Material.AIR) continue;

            if (isShulkerBox(content)) {
                if (checkShulkerContainsOrderItem(content, orderItem)) {
                    return true;
                }
            } else if (ItemUtil.hasSimilarMetadata(content, orderItem)) {
                return true;
            }
        }

        return false;
    }

    private int processShulkerDelivery(ItemStack shulkerItem, ItemStack orderItem, int maxDelivery) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return 0;
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return 0;
        }

        int totalDelivered = 0;
        Inventory shulkerInventory = shulkerBox.getInventory();
        ItemStack[] contents = shulkerInventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack content = contents[i];
            if (content == null || content.getType() == Material.AIR) continue;

            if (isShulkerBox(content)) {
                int deliveredFromNested = processShulkerDelivery(content, orderItem, maxDelivery - totalDelivered);
                totalDelivered += deliveredFromNested;

                if (content.getAmount() <= 0 || content.getType() == Material.AIR) {
                    contents[i] = null;
                }
            } else if (ItemUtil.hasSimilarMetadata(content, orderItem)) {
                int canDeliver = Math.min(content.getAmount(), maxDelivery - totalDelivered);
                if (canDeliver > 0) {
                    totalDelivered += canDeliver;

                    if (canDeliver >= content.getAmount()) {
                        contents[i] = null;
                    } else {
                        content.setAmount(content.getAmount() - canDeliver);
                    }
                }
            }

            if (totalDelivered >= maxDelivery) {
                break;
            }
        }

        shulkerInventory.setContents(contents);
        blockStateMeta.setBlockState(shulkerBox);
        shulkerItem.setItemMeta(blockStateMeta);

        return totalDelivered;
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_SHULKER_BOX") || typeName.equals("SHULKER_BOX");
    }

    @Override
    public void decorate(Player player) {
        super.decorate(player);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        super.onClose(event);
    }

}
