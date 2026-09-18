package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.api.event.PlayerOrderDeliveryCompleteEvent;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.Notifications;
import me.rein.donutorder.commons.manager.*;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.commons.objectholders.TranslationPlaceholder;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class OrderDeliveryConfirmGUI extends InventoryGUI {
    private final AtomicBoolean throughClose = new AtomicBoolean(false);
    private final AtomicBoolean processingDelivery = new AtomicBoolean(false);
    private static final Map<UUID, Long> lastActionTime = new HashMap<>();
    private static final long ACTION_COOLDOWN = 1000;

    private final Order order;
    private final Map<Integer, ItemStack> items;

    public OrderDeliveryConfirmGUI(
            Order order,
            Map<Integer, ItemStack> items
    ) {
        super(ConfigSelector.ORDER_DELIVERY_CONFIRM.getConfig().getString("title"));

        this.order = order;
        this.items = items;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (throughClose.get())
            return;

        Player player = (Player) event.getPlayer();
        OScheduler.sync().runAtEntityLater(() -> {
            if (!player.isOnline()) {
                for (ItemStack item : items.values()) {
                    if (item == null || item.getType() == Material.AIR) continue;
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                return;
            }

            DonutOrder.getGUIManager().openGUI(
                    new OrderDeliveryGUI(
                            order,
                            items
                    ),
                    player
            );
        }, player, 1L);
    }

    @Override
    public void decorate(Player player) {
        NormalItem deliveryOrder = ItemManager.getInstance().getNormalItem(ConfigType.ORDER_DELIVERY_CONFIRM,
                "delivery_order");
        this.addButton(deliveryOrder.getSlot(), new InventoryButton()
                .async(true)
                .sound(deliveryOrder.getSound())
                .creator(p -> {
                    ItemBuilder itemBuilder = deliveryOrder.getItem()
                            .namePlaceholder("{playerName}", order.getPlayerName())
                            .lorePlaceholders(player,
                                    "{itemAmount}", NumberUtil.formatNumber(order.getTotalAmount()),
                                    "{itemPrice}", NumberUtil.formatPrice(order.getPricePerItem()),
                                    "{deliveryAmount}", NumberUtil.formatNumber(calcSize())
                            );

                    itemBuilder.type(order.getItem().getType());

                    if (!ValuesManager.getInstance().isLocalization()) {
                        itemBuilder.lorePlaceholders(null, "{itemType}",
                                NameUtil.formatMaterialName(
                                        "{itemType}", itemBuilder.getType().name()));
                    } else {
                        ItemUtil.replaceWithTranslatable(
                                itemBuilder,
                                "{itemType}",
                                itemBuilder.translationKey()
                        );
                    }

                    return itemBuilder;
                })
                .consumer(event -> {})
        );

        NormalItem cancel = ItemManager.getInstance().getNormalItem(ConfigType.ORDER_DELIVERY_CONFIRM, "cancel");
        this.addButton(cancel.getSlot(), new InventoryButton()
                .sound(cancel.getSound())
                .creator(p -> cancel.getItem())
                .consumer(event -> {
                    throughClose.set(true);

                    DonutOrder.getGUIManager().openGUI(
                            new OrderDeliveryGUI(
                                    order,
                                    items
                            ),
                            player
                    );
                })
        );

        NormalItem confirm = ItemManager.getInstance().getNormalItem(ConfigType.ORDER_DELIVERY_CONFIRM, "confirm");
        this.addButton(confirm.getSlot(), new InventoryButton()
                .async(true)
                .sound(confirm.getSound())
                .creator(p -> {
                    ItemBuilder itemBuilder = confirm.getItem();

                    itemBuilder.lorePlaceholder(player,
                            "{moneyForDelivery}",
                            NumberUtil.formatPrice(calcSize() * order.getPricePerItem()));

                    return itemBuilder;
                })
                .consumer(event -> {
                    if (items.isEmpty()) {
                        return;
                    }

                    if (!canPerformAction(player)) {
                        return;
                    }

                    if (processingDelivery.get()) {
                        player.sendMessage(ChatUtil.c("&cYour delivery is already being processed."));
                        return;
                    }

                    if (!processingDelivery.compareAndSet(false, true)) {
                        player.sendMessage(ChatUtil.c("&cYour delivery is already being processed."));
                        return;
                    }

                    if (!OrderAntiDupeManager.getInstance().beginDelivery(order.getOrderId())) {
                        processingDelivery.set(false);
                        player.sendMessage(ChatUtil.c("&cThis order is currently being cancelled or already processed."));
                        return;
                    }

                    try {
                        Order currentOrder = DatabaseManager.getDatabase().getOrder(order.getOrderId());
                        if (currentOrder == null) {
                            player.sendMessage(ChatUtil.c("&cThis order no longer exists."));
                            returnItemsToPlayer(player);
                            throughClose.set(true);
                            player.closeInventory();
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingDelivery.set(false);
                            return;
                        }

                        long amountNeededToBeDelivered = currentOrder.getTotalAmount() - currentOrder.getDeliveredAmount();
                        if (amountNeededToBeDelivered <= 0) {
                            player.sendMessage(ChatUtil.c("&cThis order has already been completed."));
                            returnItemsToPlayer(player);
                            throughClose.set(true);
                            player.closeInventory();
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingDelivery.set(false);
                            return;
                        }

                        order.setDeliveredAmount(currentOrder.getDeliveredAmount());
                        order.setTotalPaid(currentOrder.getTotalPaid());

                        long totalDelivered = 0;
                        List<Integer> slotsToRemove = new ArrayList<>();

                        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
                            ItemStack draggedItem = entry.getValue();

                            if (draggedItem == null) continue;

                            if (!isShulkerBox(draggedItem) || (order.getItem().getType() == XUtil.crossMaterial("SHULKER_BOX"))) {
                                if (ItemUtil.hasSimilarMetadata(draggedItem, order.getItem())) {
                                    int canDeliver = (int) Math.min(draggedItem.getAmount(),
                                            amountNeededToBeDelivered - totalDelivered);
                                    if (canDeliver > 0) {
                                        totalDelivered += canDeliver;

                                        if (canDeliver < draggedItem.getAmount()) {
                                            ItemStack remainder = draggedItem.clone();
                                            remainder.setAmount(draggedItem.getAmount() - canDeliver);
                                            items.put(entry.getKey(), remainder);
                                        } else {
                                            slotsToRemove.add(entry.getKey());
                                        }
                                    }
                                }
                            } else {
                                long deliveredFromShulker = processShulkerDelivery(draggedItem, order.getItem(),
                                        amountNeededToBeDelivered - totalDelivered);
                                if (deliveredFromShulker > 0) {
                                    totalDelivered += deliveredFromShulker;
                                }
                            }

                            if (totalDelivered >= amountNeededToBeDelivered) {
                                break;
                            }
                        }

                        for (Integer slot : slotsToRemove) {
                            items.remove(slot);
                        }

                        if (totalDelivered <= 0) {
                            player.sendMessage(ChatUtil.c("&cNo valid items were delivered."));
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingDelivery.set(false);
                            return;
                        }

                        if (totalDelivered > amountNeededToBeDelivered) {
                            totalDelivered = amountNeededToBeDelivered;
                        }

                        final long finalDeliveredAmount = currentOrder.getDeliveredAmount() + totalDelivered;

                        if (finalDeliveredAmount > currentOrder.getTotalAmount()) {
                            player.sendMessage(ChatUtil.c("&cError: Delivery would exceed order amount."));
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingDelivery.set(false);
                            return;
                        }

                        currentOrder.setDeliveredAmount(finalDeliveredAmount);
                        double totalPaid = finalDeliveredAmount * currentOrder.getPricePerItem();
                        currentOrder.setTotalPaid(totalPaid);

                        double playerPayment = totalDelivered * currentOrder.getPricePerItem();

                        if (Double.isNaN(playerPayment) || Double.isInfinite(playerPayment) || playerPayment <= 0) {
                            player.sendMessage(ChatUtil.c("&cError: Invalid payment amount. Order may be corrupted."));
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingDelivery.set(false);
                            return;
                        }

                        CurrencyManager.getInstance().deposit(player, playerPayment);

                        OrderManager.getInstance().updateOrder(currentOrder);

                        String itemType = currentOrder.getItem().hasItemMeta() && currentOrder.getItem().getItemMeta().hasDisplayName()
                                ? ChatColor.stripColor(currentOrder.getItem().getItemMeta().getDisplayName())
                                : NameUtil.formatMaterialName(
                                "{itemType}", currentOrder.getItem().getType().name());

                        final long finalTotalDelivered = totalDelivered;
                        if (itemType.equalsIgnoreCase("{itemType}")) {
                            Messages.ON_DELIVER.sendWithDelay(player,
                                    new Object[]{
                                            "{amount}", finalTotalDelivered + "",
                                            "{totalPrice}", NumberUtil.formatPrice(playerPayment),
                                            "{itemType}", new TranslationPlaceholder(
                                            "{itemType}", currentOrder.getItem().getType().translationKey())
                                    },
                                    "Delivering...", "Delivering..", "Delivering.",
                                    "Delivering...", "Delivering..", "Delivering."
                            );
                        } else {
                            Messages.ON_DELIVER.sendWithDelay(player,
                                    new String[]{
                                            "{amount}", finalTotalDelivered + "",
                                            "{itemType}", itemType,
                                            "{totalPrice}", NumberUtil.formatPrice(playerPayment)
                                    },
                                    "Delivering...", "Delivering..", "Delivering.",
                                    "Delivering...", "Delivering..", "Delivering."
                            );
                        }

                        final Order finalCurrentOrder = currentOrder;
                        OScheduler.async().run(() -> {
                            try {
                                DatabaseManager.getDatabase().updateDeliveredAmount(finalCurrentOrder.getOrderId(), finalDeliveredAmount);
                                DatabaseManager.getDatabase().updateTotalPaid(finalCurrentOrder.getOrderId(), totalPaid);

                                order.setDeliveredAmount(finalDeliveredAmount);
                                order.setTotalPaid(totalPaid);

                                OScheduler.sync().runAtEntity(() -> {
                                    Bukkit.getPluginManager().callEvent(new PlayerOrderDeliveryCompleteEvent(
                                            player,
                                            order
                                    ));
                                }, player);

                                WebhookManager.getInstance()
                                        .logOrderDeliver(player, order, finalTotalDelivered, playerPayment);
                            } finally {
                                if (finalDeliveredAmount >= finalCurrentOrder.getTotalAmount()) {
                                    OrderAntiDupeManager.getInstance().completeOperation(
                                            order.getOrderId(),
                                            OrderAntiDupeManager.OrderOperation.COMPLETED
                                    );
                                } else {
                                    OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                                }
                                processingDelivery.set(false);
                            }
                        });

                        throughClose.set(true);
                        DonutOrder.getGUIManager().openGUI(new OrderDeliveryGUI(order, items), player);

                        OfflinePlayer playerWhoGotSent = Bukkit.getOfflinePlayer(currentOrder.getCreatorId());
                        if (!playerWhoGotSent.isOnline())
                            return;

                        String itemName = currentOrder.getItem().hasItemMeta() &&
                                currentOrder.getItem().getItemMeta().hasDisplayName()
                                ? ChatColor.stripColor(currentOrder.getItem().getItemMeta().getDisplayName())
                                : NameUtil.formatMaterialName(
                                "{itemName}", currentOrder.getItem().getType().name());

                        if (itemName.equalsIgnoreCase("{itemName}")) {
                            Notifications.ITEMS_DELIVERED.push(playerWhoGotSent.getPlayer(),
                                    "{amount}", finalTotalDelivered + "",
                                    "{senderName}", player.getName(),
                                    "{itemName}", new TranslationPlaceholder("{itemName}",
                                            currentOrder.getItem().getType().translationKey())
                            );
                            return;
                        }

                        Notifications.ITEMS_DELIVERED.push(playerWhoGotSent.getPlayer(),
                                "{amount}", finalTotalDelivered + "",
                                "{itemName}", itemName,
                                "{senderName}", player.getName()
                        );
                    } catch (Exception e) {
                        e.printStackTrace();
                        OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                        processingDelivery.set(false);
                        throw e;
                    }
                })
        );

        fillDecorationsFromConfig(ConfigType.ORDER_DELIVERY_CONFIRM);

        super.decorate(player);
    }

    private void returnItemsToPlayer(Player player) {
        for (ItemStack item : items.values()) {
            if (item == null || item.getType() == Material.AIR) continue;

            Map<Integer, ItemStack> leftover = player.getInventory().addItem(item);
            if (!leftover.isEmpty()) {
                for (ItemStack dropItem : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), dropItem);
                }
            }
        }
    }

    private long calcSize() {
        long totalAmount = 0;

        for (Map.Entry<Integer, ItemStack> entry : items.entrySet()) {
            ItemStack item = entry.getValue();
            if (item == null || item.getType() == Material.AIR) continue;

            if (!isShulkerBox(item) || (order.getItem().getType() == XUtil.crossMaterial("SHULKER_BOX"))) {
                if (ItemUtil.hasSimilarMetadata(item, order.getItem())) {
                    totalAmount += item.getAmount();
                }
            } else {
                totalAmount += countItemsInShulker(item, order.getItem());
            }
        }

        return totalAmount;
    }

    private long countItemsInShulker(ItemStack shulkerItem, ItemStack orderItem) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return 0;
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return 0;
        }

        long count = 0;
        Inventory shulkerInventory = shulkerBox.getInventory();

        for (ItemStack content : shulkerInventory.getContents()) {
            if (content == null || content.getType() == XUtil.crossMaterial("AIR")) continue;

            if (isShulkerBox(content)) {
                count += countItemsInShulker(content, orderItem);
            } else if (ItemUtil.hasSimilarMetadata(content, orderItem)) {
                count += content.getAmount();
            }
        }

        return count;
    }

    private boolean canPerformAction(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        synchronized (lastActionTime) {
            Long lastTime = lastActionTime.get(playerId);
            if (lastTime != null && currentTime - lastTime < ACTION_COOLDOWN) {
                player.sendMessage(ChatUtil.c("&cPlease wait before trying again."));
                return false;
            }

            lastActionTime.put(playerId, currentTime);
            return true;
        }
    }

    private boolean isShulkerBox(ItemStack item) {
        if (item == null) return false;
        String typeName = item.getType().name();
        return typeName.endsWith("_SHULKER_BOX") || typeName.equals("SHULKER_BOX");
    }

    private long processShulkerDelivery(ItemStack shulkerItem, ItemStack orderItem, long maxDelivery) {
        if (!(shulkerItem.getItemMeta() instanceof BlockStateMeta blockStateMeta)) {
            return 0;
        }

        if (!(blockStateMeta.getBlockState() instanceof ShulkerBox shulkerBox)) {
            return 0;
        }

        long totalDelivered = 0;
        Inventory shulkerInventory = shulkerBox.getInventory();
        ItemStack[] contents = shulkerInventory.getContents();

        for (int i = 0; i < contents.length; i++) {
            ItemStack content = contents[i];
            if (content == null || content.getType() == Material.AIR) continue;

            if (isShulkerBox(content)) {
                long deliveredFromNested = processShulkerDelivery(content, orderItem, maxDelivery - totalDelivered);
                totalDelivered += deliveredFromNested;

                if (content.getAmount() <= 0 || content.getType() == Material.AIR) {
                    contents[i] = null;
                }
            } else if (ItemUtil.hasSimilarMetadata(content, orderItem)) {
                int canDeliver = (int) Math.min(content.getAmount(), maxDelivery - totalDelivered);
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
}