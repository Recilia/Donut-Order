package me.rein.donutorder.inventory.impl;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.api.shop.ShopHook;
import me.rein.donutorder.api.shop.ShopManager;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.manager.WebhookManager;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.TakeOnlyInventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.manager.OrderAntiDupeManager;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.math.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import me.rein.donutorder.api.event.cancellable.impl.PlayerDropAllEvent;
import me.rein.donutorder.api.event.cancellable.impl.PlayerSellAllEvent;
import me.rein.donutorder.commons.util.item.ItemUtil;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CollectItemsGUI extends TakeOnlyInventoryGUI {

    private final Order order;
    private final Map<Integer, ItemStack> allItems = new HashMap<>();
    private final Map<Integer, Integer> originalAmounts = new HashMap<>();
    private long totalItemsClaimed = 0;
    private boolean hasLock = false;
    private boolean hasProcessedClose = false;
    private final int currentPage;
    private final int maxPage;
    private final List<Integer> itemSlots;

    public CollectItemsGUI(
            Order order
    ) {
        this(
                order,
                1,
                loadItemSlots()
        );
    }

    public CollectItemsGUI(
            Order order,
            int currentPage,
            List<Integer> itemSlots
    ) {
        super(
                ConfigSelector.COLLECT_ITEMS.getConfig().getString("title")
                        .replace("{currentPage}", String.valueOf(currentPage))
                        .replace("{maxPages}", String.valueOf(calculateMaxPage(order, itemSlots.size()))), itemSlots
        );

        this.itemSlots = itemSlots;
        this.order = order;
        this.currentPage = currentPage;
        this.maxPage = calculateMaxPage(order, itemSlots.size());
    }

    private CollectItemsGUI(
            Order order,
            int currentPage,
            int maxPage,
            Map<Integer, ItemStack> allItems,
            Map<Integer, Integer> originalAmounts,
            long totalItemsClaimed,
            boolean hasLock,
            List<Integer> itemSlots
    ) {
        super(
                ConfigSelector.COLLECT_ITEMS.getConfig().getString("title")
                        .replace("{currentPage}", String.valueOf(currentPage))
                        .replace("{maxPages}", String.valueOf(maxPage)), itemSlots
        );

        this.itemSlots = itemSlots;
        this.order = order;
        this.currentPage = currentPage;
        this.maxPage = maxPage;
        this.allItems.putAll(allItems);
        this.originalAmounts.putAll(originalAmounts);
        this.totalItemsClaimed = totalItemsClaimed;
        this.hasLock = hasLock;
    }

    private static List<Integer> loadItemSlots() {
        List<Integer> slots = ConfigSelector.COLLECT_ITEMS.getConfig().getIntegerList("itemSlots");

        if (slots.isEmpty()) {
            slots = new ArrayList<>();
            for (int i = 0; i < 45; i++)
                slots.add(i);
        }

        return slots;
    }

    private static int calculateMaxPage(
            Order order,
            int itemsPerPage
    ) {
        long amountToClaim = order.getDeliveredAmount() - order.getClaimedAmount();
        if (amountToClaim <= 0)
            return 1;

        int maxStackSize = order.getItem().getMaxStackSize();
        int totalStacks = (int) Math.ceil((double) amountToClaim / maxStackSize);
        return Math.max(1, (int) Math.ceil((double) totalStacks / itemsPerPage));
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        if (currentPage == 1 && allItems.isEmpty()) {
            if (!OrderAntiDupeManager.getInstance().lockOrder(order.getOrderId())) {
                player.sendMessage(ChatUtil.c("&cThis order is currently being processed. Please try again."));
                player.closeInventory();
                return;
            }

            hasLock = true;

            if (OrderAntiDupeManager.getInstance().detectSuspiciousActivity(
                    player.getUniqueId(),
                    order.getOrderId(),
                    false)) {
                player.sendMessage(ChatUtil.c("&cToo many collection attempts. Please wait a moment."));
                OrderAntiDupeManager.getInstance().unlockOrder(order.getOrderId());
                hasLock = false;
                player.closeInventory();
                return;
            }

            Order currentOrder = DatabaseManager.getDatabase().getOrder(order.getOrderId());
            if (currentOrder == null) {
                if (hasLock) {
                    OrderAntiDupeManager.getInstance().unlockOrder(order.getOrderId());
                    hasLock = false;
                }
                player.closeInventory();
                return;
            }

            order.setClaimedAmount(currentOrder.getClaimedAmount());
            order.setDeliveredAmount(currentOrder.getDeliveredAmount());

            long amountToClaim = currentOrder.getDeliveredAmount() - currentOrder.getClaimedAmount();
            if (amountToClaim <= 0) {
                if (hasLock) {
                    OrderAntiDupeManager.getInstance().unlockOrder(order.getOrderId());
                    hasLock = false;
                }
                player.closeInventory();
                return;
            }

            ItemStack itemToGive = currentOrder.getItem().clone();
            int maxStackSize = itemToGive.getMaxStackSize();
            long remaining = amountToClaim;
            int globalSlot = 0;

            while (remaining > 0) {
                ItemStack stack = itemToGive.clone();
                int stackAmount = (int) Math.min(remaining, maxStackSize);
                stack.setAmount(stackAmount);

                allItems.put(globalSlot, stack);
                originalAmounts.put(globalSlot, stackAmount);

                remaining -= stackAmount;
                globalSlot++;
            }
        }

        displayPageItems();

        NormalItem dropAll = ItemManager.getInstance().getNormalItem(ConfigType.COLLECT_ITEMS, "drop_all");
        this.addButton(dropAll.getSlot(), new InventoryButton()
                .sound(dropAll.getSound())
                .creator(p -> dropAll.getItem())
                .consumer(event -> {
                    PlayerDropAllEvent dropAllEvent = new PlayerDropAllEvent(player, order, getCurrentPageItems());
                    Bukkit.getPluginManager().callEvent(dropAllEvent);

                    if (dropAllEvent.isCancelled())
                        return;

                    dropCurrentPageItems(player);
                }));

        ShopHook shopHook = ShopManager.getInstance().getActiveShop();
        if (shopHook != null) {
            NormalItem sellAll = ItemManager.getInstance().getNormalItem(ConfigType.COLLECT_ITEMS, "sell_all");

            if (sellAll != null) {
                this.addButton(sellAll.getSlot(), new InventoryButton()
                        .sound(sellAll.getSound())
                        .creator(p -> sellAll.getItem())
                        .consumer(event -> {
                            saveCurrentPageState();
                            PlayerSellAllEvent sellAllEvent = new PlayerSellAllEvent(player, order, new HashMap<>(allItems));
                            Bukkit.getPluginManager().callEvent(sellAllEvent);

                            if (sellAllEvent.isCancelled())
                                return;

                            sellAllPageItems(player);
                        }));
            }
        }

        NormalItem back = ItemManager.getInstance().getNormalItem(ConfigType.COLLECT_ITEMS, "back");
        if (back != null) {
            this.addButton(back.getSlot(), new InventoryButton()
                    .sound(back.getSound())
                    .creator(p -> back.getItem())
                    .consumer(event -> {
                        if (currentPage > 1) {
                            saveCurrentPageState();
                            DonutOrder.getGUIManager().openGUI(new CollectItemsGUI(order, currentPage - 1, maxPage,
                                    allItems, originalAmounts, totalItemsClaimed, hasLock, itemSlots), player);
                        }
                    }));
        }

        NormalItem next = ItemManager.getInstance().getNormalItem(ConfigType.COLLECT_ITEMS, "next");
        if (next != null) {
            this.addButton(next.getSlot(), new InventoryButton()
                    .sound(next.getSound())
                    .creator(p -> next.getItem())
                    .consumer(event -> {
                        if (currentPage < maxPage) {
                            saveCurrentPageState();
                            DonutOrder.getGUIManager().openGUI(new CollectItemsGUI(order, currentPage + 1, maxPage,
                                    allItems, originalAmounts, totalItemsClaimed, hasLock, itemSlots), player);
                        }
                    }));
        }

        fillDecorationsFromConfig(ConfigType.COLLECT_ITEMS);

        super.decorate(player);
    }

    private Map<Integer, ItemStack> getCurrentPageItems() {
        saveCurrentPageState();

        int itemsPerPage = itemSlots.size();
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
        Map<Integer, ItemStack> pageItems = new HashMap<>();

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = allItems.get(i);

            if (item == null || item.getType().isAir())
                continue;

            pageItems.put(i - startIndex, item.clone());
        }

        return pageItems;
    }

    private void displayPageItems() {
        int itemsPerPage = itemSlots.size();
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());

        for (int i = startIndex; i < endIndex; i++) {
            int displaySlot = itemSlots.get(i - startIndex);
            ItemStack item = allItems.get(i);
            if (item != null && !item.getType().isAir()) {
                inventory.setItem(displaySlot, item);
            }
        }
    }

    private void saveCurrentPageState() {
        int itemsPerPage = itemSlots.size();
        int startIndex = (currentPage - 1) * itemsPerPage;

        for (int i = 0; i < itemsPerPage; i++) {
            int globalSlot = startIndex + i;
            if (globalSlot < allItems.size()) {
                int displaySlot = itemSlots.get(i);
                ItemStack currentItem = inventory.getItem(displaySlot);
                if (currentItem != null && !currentItem.getType().isAir()) {
                    allItems.put(globalSlot, currentItem.clone());
                } else {
                    allItems.put(globalSlot, null);
                }
            }
        }
    }

    @Override
    public void onClick(
            InventoryClickEvent event
    ) {
        super.onClick(event);

        if (event.isCancelled())
            return;

        int slot = event.getSlot();
        if (!itemSlots.contains(slot))
            return;

        Player player = (Player) event.getWhoClicked();

        OScheduler.sync().runAtEntityLater(this::calculateAndUpdateItemsTaken, player, 1L);
    }

    private void calculateAndUpdateItemsTaken() {
        saveCurrentPageState();

        long newTotalClaimed = 0;

        ItemStack orderItem = order.getItem();

        for (Map.Entry<Integer, Integer> entry : originalAmounts.entrySet()) {
            int globalSlot = entry.getKey();
            int originalAmount = entry.getValue();

            ItemStack currentItem = allItems.get(globalSlot);

            if (currentItem == null ||
                    currentItem.getType().isAir() ||
                    !ItemUtil.hasSimilarMetadata(currentItem, orderItem)
            ) {

                newTotalClaimed += originalAmount;

                if (currentItem != null && !currentItem.getType().isAir()) {
                    allItems.put(globalSlot, null);
                }
            } else {
                newTotalClaimed += (originalAmount - currentItem.getAmount());
            }
        }

        if (newTotalClaimed > totalItemsClaimed) {
            long difference = newTotalClaimed - totalItemsClaimed;
            totalItemsClaimed = newTotalClaimed;

            order.setClaimedAmount(order.getClaimedAmount() + difference);

            OrderManager.getInstance().updateOrder(order);

            final long newClaimedAmount = order.getClaimedAmount();
            OScheduler.async().run(() -> {
                DatabaseManager.getDatabase().updateClaimedAmount(
                        order.getOrderId(),
                        newClaimedAmount
                );
            });
        }
    }

    private void dropCurrentPageItems(
            Player player
    ) {
        saveCurrentPageState();

        Location eyeLocation = player.getEyeLocation();
        org.bukkit.util.Vector baseDirection = eyeLocation.getDirection().multiply(0.3);

        int itemsPerPage = itemSlots.size();
        int startIndex = (currentPage - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, allItems.size());
        long itemsDroppedThisPage = 0;

        for (int i = startIndex; i < endIndex; i++) {
            ItemStack item = allItems.get(i);
            if (item != null && !item.getType().isAir()) {
                itemsDroppedThisPage += item.getAmount();

                org.bukkit.util.Vector itemDirection = baseDirection.clone().add(
                        new org.bukkit.util.Vector(
                                (Math.random() - 0.5) * 0.1,
                                (Math.random() - 0.5) * 0.1,
                                (Math.random() - 0.5) * 0.1
                        )
                );

                org.bukkit.entity.Item droppedItem = player.getWorld().dropItem(eyeLocation, item.clone());
                droppedItem.setVelocity(itemDirection);
                droppedItem.setPickupDelay(10);

                allItems.put(i, null);
                int displaySlot = itemSlots.get(i - startIndex);
                inventory.setItem(displaySlot, null);
            }
        }

        if (itemsDroppedThisPage > 0) {
            totalItemsClaimed += itemsDroppedThisPage;

            order.setClaimedAmount(order.getClaimedAmount() + itemsDroppedThisPage);

            OrderManager.getInstance().updateOrder(order);

            final long newClaimedAmount = order.getClaimedAmount();
            OScheduler.async().run(() -> {
                DatabaseManager.getDatabase().updateClaimedAmount(
                        order.getOrderId(),
                        newClaimedAmount
                );
            });
        }
    }

    private void sellAllPageItems(
            Player player
    ) {
        saveCurrentPageState();

        ShopManager shopManager = ShopManager.getInstance();
        ShopHook shopHook = shopManager.getActiveShop();
        long itemsDroppedThisPage = 0;
        double totalSellPrice = 0;

        for (Map.Entry<Integer, ItemStack> entry : allItems.entrySet()) {
            ItemStack item = entry.getValue();
            if (item != null && !item.getType().isAir()) {
                int amount = item.getAmount();
                item.setAmount(1);
                double itemPrice = shopHook.getSellPrice(player, item);
                if (itemPrice == 0) continue;

                itemsDroppedThisPage += amount;
                totalSellPrice += itemPrice * amount;

                item.setAmount(0);
            }
        }

        inventory.clear();
        allItems.clear();
        player.closeInventory();

        if (totalSellPrice > 0 && !Double.isInfinite(totalSellPrice)) {
            CurrencyManager currencyManager = CurrencyManager.getInstance();
            currencyManager.deposit(player, totalSellPrice);
            Messages.SELL_ALL.send(player, "{price}", NumberUtil.formatPrice(totalSellPrice));
        }

        if (itemsDroppedThisPage > 0) {
            totalItemsClaimed += itemsDroppedThisPage;
            handleCollect(itemsDroppedThisPage);
        }
    }

    private void handleCollect(
            long itemsDroppedThisPage
    ) {
        order.setClaimedAmount(order.getClaimedAmount() + itemsDroppedThisPage);

        OrderManager.getInstance().updateOrder(order);

        final long newClaimedAmount = order.getClaimedAmount();
        OScheduler.async().run(() -> {
            DatabaseManager.getDatabase().updateClaimedAmount(
                    order.getOrderId(),
                    newClaimedAmount
            );
        });
    }

    @Override
    protected void onItemTaken(Player player, int slot, ItemStack item) {}

    @Override
    public void onClose(
            InventoryCloseEvent event
    ) {
        if (hasProcessedClose) {
            super.onClose(event);
            return;
        }

        hasProcessedClose = true;

        saveCurrentPageState();

        long newTotalClaimed = 0;
        ItemStack orderItem = order.getItem();

        for (Map.Entry<Integer, Integer> entry : originalAmounts.entrySet()) {
            int globalSlot = entry.getKey();
            int originalAmount = entry.getValue();

            ItemStack currentItem = allItems.get(globalSlot);

            if (currentItem == null ||
                    currentItem.getType().isAir() ||
                    !ItemUtil.hasSimilarMetadata(currentItem, orderItem)
            ) {
                newTotalClaimed += originalAmount;
                if (currentItem != null && !currentItem.getType().isAir()) {
                    allItems.put(globalSlot, null);
                }
            } else {
                newTotalClaimed += (originalAmount - currentItem.getAmount());
            }
        }

        if (newTotalClaimed > totalItemsClaimed) {
            long difference = newTotalClaimed - totalItemsClaimed;
            totalItemsClaimed = newTotalClaimed;
            order.setClaimedAmount(order.getClaimedAmount() + difference);

            OrderManager.getInstance().updateOrder(order);
        }

        final UUID orderId = order.getOrderId();
        final boolean hadLock = hasLock;
        hasLock = false;

        if (totalItemsClaimed > 0) {
            final long finalClaimedAmount = order.getClaimedAmount();
            final long totalAmount = order.getTotalAmount();
            final long deliveredAmount = order.getDeliveredAmount();
            final boolean isInDeletionThreshold = order.isInDeletionThreshold();
            final long collectedThisSession = totalItemsClaimed;

            OScheduler.async().run(() -> {
                try {
                    DatabaseManager.getDatabase().updateClaimedAmount(orderId, finalClaimedAmount);

                    if (finalClaimedAmount >= totalAmount ||
                            (isInDeletionThreshold && finalClaimedAmount >= deliveredAmount)) {
                        DatabaseManager.getDatabase().deleteOrder(orderId);
                        OrderManager.getInstance().removeOrder(orderId);
                    }

                    if (event.getPlayer() instanceof org.bukkit.entity.Player p)
                        WebhookManager.getInstance()
                                .logOrderCollect(p, order, collectedThisSession);

                } finally {
                    if (hadLock) {
                        OrderAntiDupeManager.getInstance().unlockOrder(orderId);
                    }
                }
            });
        } else {
            if (hadLock) {
                OrderAntiDupeManager.getInstance().unlockOrder(orderId);
            }
        }

        super.onClose(event);
    }

    @Override
    protected void finalize() throws Throwable {
        if (hasLock)
            OrderAntiDupeManager.getInstance().unlockOrder(order.getOrderId());

        super.finalize();
    }

}
