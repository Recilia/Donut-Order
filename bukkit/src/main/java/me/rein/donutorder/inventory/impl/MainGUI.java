package me.rein.donutorder.inventory.impl;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.exception.SignGUIVersionException;
import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commands.args.impl.admin.Admin;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.BulletItem;
import me.rein.donutorder.commons.objectholders.NormalItem;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.item.ItemUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MainGUI extends InventoryGUI {

    private static final Map<UUID, Long> lastOpen = new ConcurrentHashMap<>();
    private static final long OPEN_COOLDOWN_MS = 100;

    private final int currentPage;
    private final int maxPage;
    private final Filter filter;
    private final SortOrders sort;
    private final String search;
    private final List<Order> pageOrders;
    private final List<Integer> orderSlots;

    private MainGUI(
            Filter filter,
            SortOrders sort,
            String search,
            int currentPage,
            int maxPage,
            List<Order> pageOrders,
            List<Integer> orderSlots
    ) {
        super(
                ConfigSelector.ORDERS.getConfig().getString("title")
                .replace("{currentPage}", String.valueOf(currentPage))
                .replace("{maxPages}", String.valueOf(maxPage))
        );

        this.currentPage = currentPage;
        this.maxPage = maxPage;
        this.filter = filter;
        this.sort = sort;
        this.search = search;
        this.pageOrders = pageOrders;
        this.orderSlots = orderSlots;
    }

    /*
     * before, when the gui was opened, the database query to get orders was always called
     * and in case the option cross-server was enabled, it would cause huge amounts of lag
     * due to the orders being deserialized on the main thread
     *
     * so for now, use this method to fully open this with deserialization async
     */
    public static void open(
            Player player,
            Filter filter,
            SortOrders sort,
            String search,
            int page
    ) {
        UUID playerId = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long last = lastOpen.get(playerId);
        if (last != null && (now - last) < OPEN_COOLDOWN_MS)
            return;

        lastOpen.put(playerId, now);

        OScheduler.async().run(() -> {
            List<Integer> orderSlots = ConfigSelector.ORDERS.getConfig().getIntegerList("orderSlots");

            if (orderSlots.isEmpty()) {
                orderSlots = new ArrayList<>();

                for (int i = 0; i < 45; i++)
                    orderSlots.add(i);
            }

            int ordersPerPage = orderSlots.size();

            List<Order> orders = OrderManager.getInstance().getPageOrders(page, filter, sort, search, player, ordersPerPage);
            int maxPage = OrderManager.getInstance().getMaxPages(filter, search, player, ordersPerPage);
            int safePage = Math.max(1, Math.min(page, Math.max(1, maxPage)));

            List<Integer> finalOrderSlots = orderSlots;
            OScheduler.sync().runAtEntity(() -> {
                if (!player.isOnline())
                    return;

                MainGUI gui = new MainGUI(filter, sort, search, safePage, maxPage, orders, finalOrderSlots);
                DonutOrder.getGUIManager().openGUI(gui, player);
            }, player);
        });
    }

    public static void open(
            Player player
    ) {
        open(player, Filter.ALL, SortOrders.MOST_PAID, "", 1);
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, super.title);
    }

    @Override
    public void decorate(
            Player player
    ) {


        NormalItem actualOrder = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "actual_order");
        for (int i = 0; i < pageOrders.size() && i < orderSlots.size(); i++) {
            Order order = pageOrders.get(i);

            if (order.getItem() == null) {
                Bukkit.getLogger().severe("Null item detected in order " + order.getOrderId());
                continue;
            }

            boolean debug = Admin.getDebugMode().contains(player.getUniqueId());
            NormalItem actualOrderEdit = ItemManager.getInstance().getNormalItem(ConfigType.YOUR_ORDERS, "actual_order");

            int slot = orderSlots.get(i);
            this.addButton(slot, new InventoryButton()
                    .async(true)
                    .sound(actualOrderEdit.getSound())
                    .creator(p -> {
                        ItemBuilder orderItem = new ItemBuilder(order.getItem())
                                .lore2(player, actualOrder.getLore())
                                .name(actualOrder.getDisplayName());

                        orderItem
                                .namePlaceholder("{playerName}", order.getPlayerName());

                        orderItem
                                .lorePlaceholders(player, "{itemAmount}", NumberUtil.formatPrice(order.getTotalAmount()),
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
                                        "{playerName}", order.getPlayerName(),
                                        "{shouldUsea}", order.getTotalAmount() > 1 ? "" : " a"
                                );

                        if (ItemUtil.hasNoDisplayName(order.getItem())) {
                            ItemUtil.replaceWithTranslatable(
                                    orderItem,
                                    "{itemType}",
                                    orderItem.translationKey()
                            );
                        }

                        return orderItem;
                    })
                    .consumer(event -> {
                        if (order.getCreatorId().equals(player.getUniqueId()) && !debug) {
                            ItemBuilder orderItemEdit = new ItemBuilder(order.getItem())
                                    .lore2(player, actualOrderEdit.getLore())
                                    .name(actualOrderEdit.getDisplayName());

                            orderItemEdit
                                    .namePlaceholder("{playerName}", player.getName());

                            orderItemEdit
                                    .lorePlaceholders(player, "{itemAmount}", NumberUtil.formatPrice(order.getTotalAmount()),
                                            "{itemPrice}", NumberUtil.formatPrice(order.getPricePerItem()),
                                            "{itemType}", order.getItem().hasItemMeta() && order.getItem().getItemMeta().hasDisplayName()
                                                    ? ChatColor.stripColor(order.getItem().getItemMeta().getDisplayName())
                                                    : NameUtil.formatMaterialName(
                                                    "{itemType}", order.getItem().getType().name()),
                                            "{currentlyDelivered}", NumberUtil.formatPrice(order.getDeliveredAmount()),
                                            "{totalDelivery}", NumberUtil.formatPrice(order.getTotalAmount()),
                                            "{totalPaid}", NumberUtil.formatPrice(order.getTotalPaid()),
                                            "{totalPrice}", NumberUtil.formatPrice(order.getTotalCost()),
                                            "{formattedExpirationTime}", TimeUtil.formatTimeConcise(order.getRemainingTime()));

                            if (ValuesManager.getInstance().isLocalization()) {
                                ItemUtil.replaceWithTranslatable(
                                        orderItemEdit,
                                        "{itemType}",
                                        orderItemEdit.translationKey()
                                );
                            }

                            DonutOrder.getGUIManager().openGUI(new EditOrderGUI(order, actualOrderEdit, player), player);
                        } else {
                            DonutOrder.getGUIManager().openGUI(new OrderDeliveryGUI(order), player);
                        }
                    }));
        }

        BulletItem sortItem = ItemManager.getInstance().getBulletItem(ConfigType.ORDERS, "sort");
        this.addButton(sortItem.getSlot(), new InventoryButton()
                .sound(sortItem.getSound())
                .creator(p -> sortItem.getItem(sort))
                .consumer(event -> {
                    SortOrders nextSort = (SortOrders) sortItem.getNextEnumOption(sort);
                    MainGUI.open(player, filter, nextSort, search, currentPage);
                }));

        BulletItem filterItem = ItemManager.getInstance().getBulletItem(ConfigType.ORDERS, "filter");
        this.addButton(filterItem.getSlot(), new InventoryButton()
                .sound(filterItem.getSound())
                .creator(p -> filterItem.getItem(filter))
                .consumer(event -> {
                    Filter nextFilter = (Filter) filterItem.getNextEnumOption(filter);
                    MainGUI.open(player, nextFilter, sort, search, currentPage);
                }));

        NormalItem searchItem = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "search");
        this.addButton(searchItem.getSlot(), new InventoryButton()
                .sound(searchItem.getSound())
                .creator(p -> searchItem.getItem())
                .consumer(event -> {
                    player.closeInventory();

                    try {
                        SignGUI.builder()
                                .setLines(Messages.SIGN_SEARCH.getLine(0),
                                        Messages.SIGN_SEARCH.getLine(1),
                                        Messages.SIGN_SEARCH.getLine(2),
                                        Messages.SIGN_SEARCH.getLine(3))
                                .setHandler((p, result) -> {
                                    String searchTerm = result.getLine(0).trim();
                                    MainGUI.open(player, filter, sort, searchTerm, 1);
                                    return Collections.emptyList();
                                })
                                .build()
                                .open(player);
                    } catch (SignGUIVersionException e) {}
                }));

        NormalItem ordersItem = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "orders");
        this.addButton(ordersItem.getSlot(), new InventoryButton()
                .sound(ordersItem.getSound())
                .creator(p -> ordersItem.getItem())
                .consumer(event -> {
                    MainGUI.open(player, filter, sort, "", 1);
                }));

        NormalItem yourOrdersItem = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "your_orders");
        this.addButton(yourOrdersItem.getSlot(), new InventoryButton()
                .sound(yourOrdersItem.getSound())
                .creator(p -> yourOrdersItem.getItem())
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new YourOrdersGUI(), player);
                }));

        NormalItem backItem = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "back");
        this.addButton(backItem.getSlot(), new InventoryButton()
                .sound(backItem.getSound())
                .creator(p -> backItem.getItem())
                .consumer(event -> {
                    if (currentPage > 1) {
                        MainGUI.open(player, filter, sort, search, currentPage - 1);
                    }
                }));

        NormalItem nextItem = ItemManager.getInstance().getNormalItem(ConfigType.ORDERS, "next");
        this.addButton(nextItem.getSlot(), new InventoryButton()
                .sound(nextItem.getSound())
                .creator(p -> nextItem.getItem())
                .consumer(event -> {
                    if (currentPage < maxPage) {
                        MainGUI.open(player, filter, sort, search, currentPage + 1);
                    }
                }));

        fillDecorationsFromConfig(ConfigType.ORDERS);

        super.decorate(player);
    }
}