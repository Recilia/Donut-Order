package me.rein.donutorder.inventory.impl.admin;

import de.rapha149.signgui.SignGUI;
import de.rapha149.signgui.exception.SignGUIVersionException;
import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AdminOrdersGUI extends InventoryGUI {

    private static final String SELECTED_COLOR = "#E63A35";
    private static final String UNSELECTED_COLOR = "#858585";
    private static final String BULLET = "▪ ";

    private final int currentPage;
    private final int maxPage;
    private final Filter filter;
    private final SortOrders sort;
    private final String search;
    private List<Order> pageOrders;

    public AdminOrdersGUI(
            Filter filter,
            SortOrders sort,
            String search,
            int currentPage
    ) {
        super(
                "ᴀᴅᴍɪɴ → Orders (" + currentPage + "/" + Math.max(1,
                        OrderManager.getInstance().getMaxPages(filter, search)) + ")"
        );

        this.currentPage = currentPage;
        this.maxPage = Math.max(1, OrderManager.getInstance().getMaxPages(filter, search));
        this.filter = filter;
        this.sort = sort;
        this.search = search;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, super.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        pageOrders = OrderManager.getInstance().getPageOrders(currentPage, filter, sort, search);

        for (int i = 0; i < Math.min(pageOrders.size(), 45); i++) {
            Order order = pageOrders.get(i);

            if (order.getItem() == null) {
                Bukkit.getLogger().severe("Null item detected in order " + order.getOrderId());
                continue;
            }

            this.addButton(i, new InventoryButton()
                    .creator(p -> {
                        String itemName = getItemName(order);

                        String statusColor = order.isComplete() ? "#00FC00" :
                                order.isInDeletionThreshold() ? "#FC0000" : "#FFA500";
                        String status = order.isComplete() ? "Complete" :
                                order.isInDeletionThreshold() ? "Expired" : "Active";

                        return new ItemBuilder(order.getItem().clone())
                                .name("#FF6B6B" + order.getOrderId().toString().substring(0, 8))
                                .lore(
                                        player,
                                        "",
                                        "#858585ᴘʟᴀʏᴇʀ: #FFFFFF" + order.getPlayerName(),
                                        "#858585ɪᴛᴇᴍ: #FFFFFF" + itemName,
                                        "#858585ᴘʀᴏɢʀᴇss: #FFFFFF" + NumberUtil.formatPrice(order.getDeliveredAmount())
                                                + "#858585/" + NumberUtil.formatPrice(order.getTotalAmount()),
                                        "#858585ᴘʀɪᴄᴇ: #00FC00$" + NumberUtil.formatPrice(order.getPricePerItem()) + " #858585ᴇᴀᴄʜ",
                                        "#858585ᴛᴏᴛᴀʟ: #00FC00$" + NumberUtil.formatPrice(order.getTotalCost()),
                                        "#858585ᴛɪᴍᴇ: #FFA500" + TimeUtil.formatTimeConcise(order.getRemainingTime()),
                                        "#858585sᴛᴀᴛᴜs: " + statusColor + status,
                                        "",
                                        "#FC0000☒ ʟᴇꜰᴛ-ᴄʟɪᴄᴋ → ᴅᴇʟᴇᴛᴇ",
                                        "#00B4DB◎ ʀɪɢʜᴛ-ᴄʟɪᴄᴋ → ᴅᴇᴛᴀɪʟs"
                                );
                    })
                    .consumer(event -> {
                        if (event.isLeftClick()) {
                            DonutOrder.getGUIManager().openGUI(
                                    new AdminOrderConfirmGUI(order, filter, sort, search, currentPage),
                                    player
                            );
                        } else {
                            DonutOrder.getGUIManager().openGUI(
                                    new AdminOrderDetailGUI(order, filter, sort, search, currentPage),
                                    player
                            );
                        }
                    }));
        }

        this.addButton(45, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("ARROW"))
                        .name("#FC0000← ʙᴀᴄᴋ")
                        .lore(player, "#858585Return to admin panel"))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new AdminGUI(), player);
                }));

        this.addButton(47, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("CAULDRON"))
                        .name("#E63A35sᴏʀᴛ")
                        .lore(player, buildSortLore().toArray(new String[0])))
                .consumer(event -> {
                    SortOrders nextSort = getNextSort(sort);
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(filter, nextSort, search, 1),
                            player
                    );
                }));

        this.addButton(48, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("HOPPER"))
                        .name("#E63A35ꜰɪʟᴛᴇʀ")
                        .lore(player, buildFilterLore().toArray(new String[0])))
                .consumer(event -> {
                    Filter nextFilter = getNextFilter(filter);
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(nextFilter, sort, search, 1),
                            player
                    );
                }));

        this.addButton(49, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("NAME_TAG"))
                        .name("#E63A35sᴇᴀʀᴄʜ")
                        .lore(
                                player,
                                search.isEmpty() ? "#858585No active search" : "#858585Query: #FFFFFF" + search,
                                "",
                                "#858585Left-click to search",
                                "#858585Right-click to clear"
                        ))
                .consumer(event -> {
                    if (event.isRightClick() && !search.isEmpty()) {
                        DonutOrder.getGUIManager().openGUI(
                                new AdminOrdersGUI(filter, sort, "", 1),
                                player
                        );
                        return;
                    }

                    player.closeInventory();
                    try {
                        SignGUI.builder()
                                .setLines("", "^^^^^^^^^^^", "Enter player", "or item name")
                                .setHandler((p, result) -> {
                                    String searchTerm = result.getLine(0).trim();
                                    OScheduler.sync().runAtEntity(() -> {
                                        DonutOrder.getGUIManager().openGUI(
                                                new AdminOrdersGUI(filter, sort, searchTerm, 1),
                                                player
                                        );
                                    }, player);
                                    return Collections.emptyList();
                                })
                                .build()
                                .open(player);
                    } catch (SignGUIVersionException e) {
                        player.sendMessage(ChatUtil.c("&cSign GUI not supported!"));
                    }
                }));

        this.addButton(51, new InventoryButton()
                .creator(p -> new ItemBuilder(currentPage > 1 ? XUtil.crossMaterial("ARROW") : XUtil.crossMaterial("GRAY_DYE"))
                        .name(currentPage > 1 ? "#00FC00← ᴘʀᴇᴠɪᴏᴜs" : "#858585← ᴘʀᴇᴠɪᴏᴜs")
                        .lore(player, currentPage > 1 ? "#858585Page " + (currentPage - 1) : "#858585First page"))
                .consumer(event -> {
                    if (currentPage > 1) {
                        DonutOrder.getGUIManager().openGUI(
                                new AdminOrdersGUI(filter, sort, search, currentPage - 1),
                                player
                        );
                    }
                }));

        this.addButton(52, new InventoryButton()
                .creator(p -> new ItemBuilder(currentPage < maxPage ? XUtil.crossMaterial("ARROW") : XUtil.crossMaterial("GRAY_DYE"))
                        .name(currentPage < maxPage ? "#00FC00ɴᴇxᴛ →" : "#858585ɴᴇxᴛ →")
                        .lore(player, currentPage < maxPage ? "#858585Page " + (currentPage + 1) : "#858585Last page"))
                .consumer(event -> {
                    if (currentPage < maxPage) {
                        DonutOrder.getGUIManager().openGUI(
                                new AdminOrdersGUI(filter, sort, search, currentPage + 1),
                                player
                        );
                    }
                }));

        ItemBuilder filler = new ItemBuilder(XUtil.crossMaterial("GRAY_STAINED_GLASS_PANE")).name(" ");

        for (int i = 45; i < 54; i++) {
            if (getInventory().getItem(i) != null)
                continue;

            getInventory().setItem(i, filler);
        }

        super.decorate(player);
    }

    private String getItemName(Order order) {
        if (order.getItem().hasItemMeta() && order.getItem().getItemMeta().hasDisplayName())
            return ChatColor.stripColor(order.getItem().getItemMeta().getDisplayName());

        String materialName = order.getItem().getType().name();
        return formatMaterialName(materialName);
    }

    private String formatMaterialName(
            String materialName
    ) {
        String[] words = materialName.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty())
                continue;

            formatted.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }

        return formatted.toString().trim();
    }

    private List<String> buildSortLore() {
        List<String> lore = new ArrayList<>();

        for (SortOrders sortOption : SortOrders.values()) {
            String color = sortOption == sort ? SELECTED_COLOR : UNSELECTED_COLOR;
            lore.add(color + BULLET + formatEnumName(sortOption.name()));
        }

        return lore;
    }

    private List<String> buildFilterLore() {
        List<String> lore = new ArrayList<>();

        for (Filter filterOption : Filter.values()) {
            String color = filterOption == filter ? SELECTED_COLOR : UNSELECTED_COLOR;
            lore.add(color + BULLET + formatEnumName(filterOption.name()));
        }

        return lore;
    }

    private String formatEnumName(
            String name
    ) {
        String[] words = name.toLowerCase().split("_");
        StringBuilder formatted = new StringBuilder();

        for (String word : words) {
            if (word.isEmpty())
                continue;

            formatted.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1))
                    .append(" ");
        }

        return formatted.toString().trim();
    }

    private SortOrders getNextSort(SortOrders current) {
        SortOrders[] values = SortOrders.values();
        return values[(current.ordinal() + 1) % values.length];
    }

    private Filter getNextFilter(Filter current) {
        Filter[] values = Filter.values();
        return values[(current.ordinal() + 1) % values.length];
    }
}