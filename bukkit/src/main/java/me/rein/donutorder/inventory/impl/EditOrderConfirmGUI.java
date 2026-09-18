package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.manager.WebhookManager;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.OrderAntiDupeManager;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public class EditOrderConfirmGUI extends InventoryGUI {

    private final Order order;
    private final NormalItem template;
    private final Player originalPlayer;
    private final AtomicBoolean processingCancellation = new AtomicBoolean(false);

    private static final Map<UUID, Long> lastActionTime = new HashMap<>();
    private static final long ACTION_COOLDOWN = 1000;

    public EditOrderConfirmGUI(
            Order order,
            NormalItem template,
            Player player
    ) {
        super(
                ConfigSelector.EDIT_ORDER_CONFIRM.getConfig().getString("title")
        );

        this.order = order;
        this.template = template;
        this.originalPlayer = player;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    private boolean canPerformAction(
            Player player
    ) {
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

    private ItemBuilder buildOrderItem(
            Player player
    ) {
        ItemBuilder orderItem = new ItemBuilder(order.getItem())
                .lore2(player, template.getLore())
                .name(template.getDisplayName())
                .namePlaceholder("{playerName}", player.getName())
                .lorePlaceholders(player,
                        "{itemAmount}", NumberUtil.formatPrice(order.getTotalAmount()),
                        "{itemPrice}", NumberUtil.formatPrice(order.getPricePerItem()),
                        "{itemType}", order.getItem().hasItemMeta() && order.getItem().getItemMeta().hasDisplayName()
                                ? ChatColor.stripColor(order.getItem().getItemMeta().getDisplayName())
                                : NameUtil.formatMaterialName("{itemType}", order.getItem().getType().name()),
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

    @Override
    public void decorate(
            Player player
    ) {
        NormalItem cancel = ItemManager.getInstance().getNormalItem(ConfigType.EDIT_ORDER_CONFIRM, "cancel");
        this.addButton(cancel.getSlot(), new InventoryButton()
                .sound(cancel.getSound())
                .creator(p -> cancel.getItem())
                .consumer(event -> {
                    if (!canPerformAction(player)) {
                        return;
                    }

                    DonutOrder.getGUIManager().openGUI(new EditOrderGUI(order, template, player), player);
                }));

        this.addButton(13, new InventoryButton()
                .async(true)
                .creator(p -> buildOrderItem(p))
                .consumer(event -> {}));

        NormalItem confirm = ItemManager.getInstance().getNormalItem(ConfigType.EDIT_ORDER_CONFIRM, "confirm");
        this.addButton(confirm.getSlot(), new InventoryButton()
                .sound(confirm.getSound())
                .creator(p -> confirm.getItem())
                .consumer(event -> {
                    if (!canPerformAction(player)) {
                        return;
                    }

                    if (OrderAntiDupeManager.getInstance().detectSuspiciousActivity(player.getUniqueId(), order.getOrderId(), false)) {
                        player.sendMessage(ChatUtil.c("&cToo many cancellation attempts. Please wait a moment and try again."));
                        return;
                    }

                    if (processingCancellation.get()) {
                        player.sendMessage(ChatUtil.c("&cYour order cancellation is already being processed."));
                        return;
                    }

                    if (!processingCancellation.compareAndSet(false, true)) {
                        player.sendMessage(ChatUtil.c("&cYour order cancellation is already being processed."));
                        return;
                    }

                    if (!OrderAntiDupeManager.getInstance().beginCancel(order.getOrderId())) {
                        processingCancellation.set(false);
                        player.sendMessage(ChatUtil.c("&cThis order is currently being delivered or already processed."));
                        return;
                    }

                    OScheduler.async().run(() -> {
                        try {
                            Order dupeCheck = DatabaseManager.getDatabase().getOrder(order.getOrderId());
                            if (dupeCheck == null) {
                                OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                                processingCancellation.set(false);
                                OScheduler.sync().runAtEntity(() -> {
                                    player.sendMessage(ChatUtil.c("&cThis order no longer exists."));
                                    DonutOrder.getGUIManager().openGUI(new YourOrdersGUI(), player);
                                }, player);
                                return;
                            }

                            // refund money for items that were never delivered
                            double dupeMoneyBack = dupeCheck.getTotalCost() - dupeCheck.getDeliveredAmount() * dupeCheck.getPricePerItem();
                            if (Double.isNaN(dupeMoneyBack) || Double.isInfinite(dupeMoneyBack))
                                dupeMoneyBack = 0;

                            // items already delivered but not yet collected by the creator
                            long uncollected = dupeCheck.getDeliveredAmount() - dupeCheck.getClaimedAmount();

                            if (uncollected > 0) {
                                // freeze the order: mark it as if it was fully delivered, with the
                                // "missing" items already claimed. This hides it from the public list,
                                // blocks new deliveries (available = 0), but lets the creator collect
                                // the real remaining items via CollectItemsGUI. Once they collect them
                                // all, the existing auto-deletion logic kicks in.
                                long syntheticDelivered = dupeCheck.getTotalAmount();
                                long syntheticClaimed = dupeCheck.getTotalAmount() - uncollected;

                                DatabaseManager.getDatabase().updateDeliveredAmount(dupeCheck.getOrderId(), syntheticDelivered);
                                DatabaseManager.getDatabase().updateClaimedAmount(dupeCheck.getOrderId(), syntheticClaimed);

                                dupeCheck.setDeliveredAmount(syntheticDelivered);
                                dupeCheck.setClaimedAmount(syntheticClaimed);
                                OrderManager.getInstance().updateOrder(dupeCheck);
                            } else {
                                // nothing to collect — delete normally
                                OrderManager.getInstance().removeOrder(order.getOrderId());
                                DatabaseManager.getDatabase().deleteOrder(order.getOrderId());
                            }

                            WebhookManager.getInstance()
                                    .logOrderCancel(player, order, dupeMoneyBack);

                            final double finalMoneyBack = dupeMoneyBack;

                            OrderAntiDupeManager.getInstance().completeOperation(
                                    order.getOrderId(),
                                    OrderAntiDupeManager.OrderOperation.CANCELLED
                            );
                            processingCancellation.set(false);

                            OScheduler.sync().runAtEntity(() -> {
                                if (finalMoneyBack > 0) {
                                    CurrencyManager.getInstance().deposit(player, finalMoneyBack);
                                }

                                Messages.ORDER_CANCELLED.sendBoth(player);
                                MainGUI.open(player);
                            }, player);
                        } catch (Exception e) {
                            e.printStackTrace();
                            OrderAntiDupeManager.getInstance().abortOperation(order.getOrderId());
                            processingCancellation.set(false);
                            OScheduler.sync().runAtEntity(() -> {
                                player.sendMessage(ChatUtil.c("&cAn error occurred while cancelling your order. Please try again later."));
                                DonutOrder.getGUIManager().openGUI(new YourOrdersGUI(), player);
                            }, player);
                        }
                    });
                }));

        fillDecorationsFromConfig(ConfigType.EDIT_ORDER_CONFIRM);

        super.decorate(player);
    }
}