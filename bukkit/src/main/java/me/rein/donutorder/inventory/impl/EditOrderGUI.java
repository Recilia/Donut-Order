package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.OrderAntiDupeManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.*;

public class EditOrderGUI extends InventoryGUI {
    private final int[] aura = {0, 1, 2, 9, 11, 18, 19, 20};
    private final Order order;
    private final NormalItem template;
    private final Player originalPlayer;
    private boolean isProcessing = false;

    private static final Map<UUID, Long> lastActionTime = new HashMap<>();
    private static final long ACTION_COOLDOWN = 1000;

    public EditOrderGUI(Order order, NormalItem template, Player player) {
        super(ConfigSelector.EDIT_ORDER.getConfig().getString("title"));
        this.order = order;
        this.template = template;
        this.originalPlayer = player;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    private boolean canPerformAction(Player player) {
        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();

        synchronized (lastActionTime) {
            Long lastTime = lastActionTime.get(playerId);
            if (lastTime != null && currentTime - lastTime < ACTION_COOLDOWN) {
                player.sendMessage("§cPlease wait before trying again.");
                return false;
            }

            lastActionTime.put(playerId, currentTime);
            return true;
        }
    }

    private ItemBuilder buildOrderItem(Player player) {
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
    public void decorate(Player player) {
        for (int i : aura) {
            this.addButton(i, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("BLACK_STAINED_GLASS_PANE"))
                            .name(" "))
                    .consumer(event -> {}));
        }

        this.addButton(10, new InventoryButton()
                .async(true)
                .creator(p -> buildOrderItem(p))
                .consumer(event -> {}));

        NormalItem cancel = ItemManager.getInstance().getNormalItem(ConfigType.EDIT_ORDER, "cancel");
        this.addButton(cancel.getSlot(), new InventoryButton()
                .sound(cancel.getSound())
                .creator(p -> cancel.getItem())
                .consumer(event -> {
                    if (!canPerformAction(player)) {
                        return;
                    }

                    if (OrderAntiDupeManager.getInstance().isOrderLocked(order.getOrderId())) {
                        player.sendMessage("§cThis order is currently being processed. Please try again later.");
                        return;
                    }

                    OScheduler.async().run(() -> {
                        if (DatabaseManager.getDatabase().getOrder(order.getOrderId()) == null) {
                            OScheduler.sync().runAtEntity(() -> {
                                player.sendMessage("§cThis order no longer exists.");
                                DonutOrder.getGUIManager().openGUI(new YourOrdersGUI(), player);
                            }, player);
                            return;
                        }

                        OScheduler.sync().runAtEntity(() -> {
                            DonutOrder.getGUIManager().openGUI(new EditOrderConfirmGUI(order, template, player), player);
                        }, player);
                    });
                }));

        NormalItem collect = ItemManager.getInstance().getNormalItem(ConfigType.EDIT_ORDER, "collect");
        this.addButton(collect.getSlot(), new InventoryButton()
                .sound(collect.getSound())
                .async(true)
                .creator(p -> {
                    ItemBuilder itemBuilder = collect.getItem();

                    if (itemBuilder.getLore() == null)
                        return itemBuilder;

                    String colorCode = "";
                    boolean hasItemList = false;

                    for (String loreKey : itemBuilder.getLore()) {
                        if (!loreKey.contains("{itemList}"))
                            continue;

                        colorCode = "&7";
                        hasItemList = true;
                        break;
                    }

                    if (!hasItemList)
                        return itemBuilder;

                    long amountToCollect = order.getDeliveredAmount() - order.getClaimedAmount();
                    long maxItemStack = collect.getItem().getType().getMaxStackSize();
                    long validAmount = maxItemStack * 6;

                    if (amountToCollect <= validAmount) {
                        validAmount = amountToCollect;
                        amountToCollect = -1;
                    }

                    List<String> newLore = new ArrayList<>(
                            itemBuilder.getLore().subList(0, itemBuilder.getLore().size() - 1)
                    );

                    while (validAmount > 0) {
                        if (maxItemStack > validAmount)
                            newLore.add(colorCode + "{itemList} x" + validAmount);
                        else
                            newLore.add(colorCode + "{itemList} x" + maxItemStack);

                        validAmount -= maxItemStack;
                    }

                    if (amountToCollect != -1)
                        newLore.add(colorCode + "and " + (amountToCollect - maxItemStack * 6) + " more...");

                    itemBuilder.lore2(p, newLore);

                    if (ValuesManager.getInstance().isLocalization()) {
                        ItemUtil.replaceWithTranslatable(
                                itemBuilder,
                                "{itemList}",
                                order.getItem().getType().translationKey()
                        );
                    } else {
                        itemBuilder.lorePlaceholders(null, "{itemList}",
                                NameUtil.formatMaterialName(
                                        "{itemType}", order.getItem().getType().name()));
                    }

                    return itemBuilder;
                })
                .consumer(event -> {
                    if (order.getDeliveredAmount() - order.getClaimedAmount() <= 0)
                        return;

                    if (OrderAntiDupeManager.getInstance().isOrderLocked(order.getOrderId())) {
                        player.sendMessage("§cThis order is currently being processed.");
                        return;
                    }

                    DonutOrder.getGUIManager().openGUI(new CollectItemsGUI(order), player);
                }));

        fillDecorationsFromConfig(ConfigType.EDIT_ORDER);

        super.decorate(player);
    }
}