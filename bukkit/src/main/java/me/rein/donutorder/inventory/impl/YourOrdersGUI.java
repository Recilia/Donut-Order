package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.PermissionManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public class YourOrdersGUI extends InventoryGUI {

    public YourOrdersGUI() {
        super(
                ConfigSelector.YOUR_ORDERS.getConfig().getString("title")
        );
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        NormalItem actualOrder = ItemManager.getInstance().getNormalItem(ConfigType.YOUR_ORDERS,
                "actual_order");
        NormalItem expiredOrder = ItemManager.getInstance().getNormalItem(ConfigType.YOUR_ORDERS,
                "expired_order");

        if (expiredOrder == null)
            expiredOrder = actualOrder;

        NormalItem finalExpiredOrder = expiredOrder;

        CompletableFuture<List<Order>> ordersFuture = CompletableFuture.supplyAsync(() ->
                DatabaseManager.getDatabase().getPlayerOrders(player.getUniqueId())
        );

        for (int i = 0; i < 26; i++) {
            final int slot = i;
            this.addButton(slot, new InventoryButton()
                    .async(true)
                    .asyncConsumer(true)
                    .creator(p -> {
                        try {
                            List<Order> playerOrders = ordersFuture.get();

                            if (slot < playerOrders.size()) {
                                Order order = playerOrders.get(slot);

                                ItemBuilder orderItem = new ItemBuilder(order.getItem())
                                        .lore2(p, order.isInDeletionThreshold() ? finalExpiredOrder.getLore() : actualOrder.getLore())
                                        .name(order.isInDeletionThreshold() ? finalExpiredOrder.getDisplayName() : actualOrder.getDisplayName());

                                orderItem.namePlaceholder("{playerName}", p.getName());

                                orderItem.lorePlaceholders(p, "{itemAmount}", NumberUtil.formatPrice(order.getTotalAmount()),
                                        "{itemPrice}", NumberUtil.formatPrice(order.getPricePerItem()),
                                        "{itemType}", order.getItem().hasItemMeta() && order.getItem().getItemMeta().hasDisplayName()
                                                ? ChatColor.stripColor(order.getItem().getItemMeta().getDisplayName())
                                                : NameUtil.formatMaterialName(
                                                "{itemType}", order.getItem().getType().name()),
                                        "{currentlyDelivered}", NumberUtil.formatPrice(order.getDeliveredAmount()),
                                        "{totalDelivery}", NumberUtil.formatPrice(order.getTotalAmount()),
                                        "{totalPaid}", NumberUtil.formatPrice(order.getTotalPaid()),
                                        "{totalPrice}", NumberUtil.formatPrice(order.getTotalCost()),
                                        "{formattedExpirationTime}", TimeUtil.formatTimeConcise(order.getRemainingTime()),
                                        "{formattedDeletionTime}", TimeUtil.formatTimeConcise(order.getDeletionRemainingTime())
                                );

                                if (ItemUtil.hasNoDisplayName(order.getItem())) {
                                    ItemUtil.replaceWithTranslatable(
                                            orderItem,
                                            "{itemType}",
                                            orderItem.translationKey()
                                    );
                                }

                                return orderItem;
                            }

                            return null;
                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }
                    })
                    .consumer(event -> {
                        try {
                            List<Order> playerOrders = ordersFuture.get();
                            if (slot < playerOrders.size()) {
                                Order order = playerOrders.get(slot);
                                NormalItem template = order.isInDeletionThreshold() ?
                                        finalExpiredOrder : actualOrder;

                                OScheduler.sync().runAtEntity(() -> {
                                    DonutOrder.getGUIManager().openGUI(new EditOrderGUI(order, template, player), player);
                                }, player);
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }));
        }

        NormalItem new_order = ItemManager.getInstance().getNormalItem(ConfigType.YOUR_ORDERS, "your_order");
        NormalItem max_orders = ItemManager.getInstance().getNormalItem(ConfigType.YOUR_ORDERS, "max_orders");

        this.addButton(26, new InventoryButton()
                .async(true)
                .asyncConsumer(true)
                .creator(p -> {
                    try {
                        List<Order> playerOrders = ordersFuture.get();
                        int orderSize = playerOrders.size();

                        if (orderSize < PermissionManager.getInstance().getMaxOrdersForPlayer(player))
                            return new ItemBuilder(new_order.getItem());

                        return new ItemBuilder(max_orders.getItem());
                    } catch (Exception e) {
                        e.printStackTrace();
                        return new ItemBuilder(new_order.getItem());
                    }
                })
                .consumer(event -> {
                    try {
                        List<Order> playerOrders = ordersFuture.get();
                        int orderSize = playerOrders.size();

                        if (orderSize < PermissionManager.getInstance().getMaxOrdersForPlayer(player)) {
                            OScheduler.sync().runAtEntity(() -> {
                                if (new_order.getSound() != null)
                                    new_order.getSound().play(player);

                                DonutOrder.getGUIManager().openGUI(
                                        new NewOrderGUI(1, 0, false, null),
                                        player);
                            }, player);
                            return;
                        }

                        OScheduler.sync().runAtEntity(() -> {
                            if (max_orders.getSound() == null)
                                return;

                            max_orders.getSound().play(player);
                        }, player);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }));

        fillDecorationsFromConfig(ConfigType.YOUR_ORDERS);

        super.decorate(player);
    }
}