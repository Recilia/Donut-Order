package me.rein.donutorder.inventory.impl.admin;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.time.TimeUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.text.SimpleDateFormat;
import java.util.Date;

public class AdminOrderDetailGUI extends InventoryGUI {

    private final Order order;
    private final Filter filter;
    private final SortOrders sort;
    private final String search;
    private final int returnPage;

    public AdminOrderDetailGUI(
            Order order,
            Filter filter,
            SortOrders sort,
            String search,
            int returnPage
    ) {
        super(
                "ᴀᴅᴍɪɴ → Order Details"
        );

        this.order = order;
        this.filter = filter;
        this.sort = sort;
        this.search = search;
        this.returnPage = returnPage;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 45, super.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        String itemName = getItemName(order);
        SimpleDateFormat sdf = new SimpleDateFormat("MMM dd, yyyy HH:mm");

        this.addButton(4, new InventoryButton()
                .creator(p -> new ItemBuilder(order.getItem().clone())
                        .amount((int) Math.min(64, order.getTotalAmount()))
                        .name("#FFA500" + itemName)
                        .lore(player, "#858585Ordered item"))
                .consumer(event -> {}));

        this.addButton(19, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("PLAYER_HEAD"))
                        .skullOwner(order.getPlayerName())
                        .name("#00B4DBᴘʟᴀʏᴇʀ")
                        .lore(
                                player,
                                "",
                                "#858585ɴᴀᴍᴇ: #FFFFFF" + order.getPlayerName(),
                                "#858585ᴜᴜɪᴅ: #777777" + order.getCreatorId().toString()
                        ))
                .consumer(event -> {}));

        this.addButton(20, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("NAME_TAG"))
                        .name("#DA70D6ᴏʀᴅᴇʀ ɪᴅ")
                        .lore(
                                player,
                                "",
                                "#858585sʜᴏʀᴛ: #FFFFFF" + order.getOrderId().toString().substring(0, 8),
                                "#858585ꜰᴜʟʟ: #777777" + order.getOrderId().toString()
                        ))
                .consumer(event -> {}));

        this.addButton(21, new InventoryButton()
                .creator(p -> {
                    double progress = (double) order.getDeliveredAmount() / order.getTotalAmount() * 100;
                    String progressBar = createProgressBar(progress);

                    return new ItemBuilder(XUtil.crossMaterial("CHEST"))
                            .name("#00FC00ǫᴜᴀɴᴛɪᴛʏ")
                            .lore(
                                    player,
                                    "",
                                    "#858585ᴛᴏᴛᴀʟ: #FFFFFF" + NumberUtil.formatPrice(order.getTotalAmount()),
                                    "#858585ᴅᴇʟɪᴠᴇʀᴇᴅ: #00FC00" + NumberUtil.formatPrice(order.getDeliveredAmount()),
                                    "#858585ᴄʟᴀɪᴍᴇᴅ: #FFA500" + NumberUtil.formatPrice(order.getClaimedAmount()),
                                    "#858585ᴀᴠᴀɪʟᴀʙʟᴇ: #FFFFFF" + NumberUtil.formatPrice(order.getAvailableAmount()),
                                    "",
                                    progressBar + " #858585" + String.format("%.1f", progress) + "%"
                            );
                })
                .consumer(event -> {}));

        this.addButton(23, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("GOLD_INGOT"))
                        .name("#FFD700ᴘʀɪᴄɪɴɢ")
                        .lore(
                                player,
                                "",
                                "#858585ᴘᴇʀ ɪᴛᴇᴍ: #00FC00$" + NumberUtil.formatPrice(order.getPricePerItem()),
                                "#858585ᴛᴏᴛᴀʟ ᴄᴏsᴛ: #00FC00$" + NumberUtil.formatPrice(order.getTotalCost()),
                                "",
                                "#858585ᴘᴀɪᴅ ᴏᴜᴛ: #00FC00$" + NumberUtil.formatPrice(order.getTotalPaid()),
                                "#858585ʀᴇᴍᴀɪɴɪɴɢ: #FFA500$" + NumberUtil.formatPrice(order.getRemainingCost())
                        ))
                .consumer(event -> {}));

        this.addButton(24, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("CLOCK"))
                        .name("#FFA500ᴛɪᴍɪɴɢ")
                        .lore(
                                player,
                                "",
                                "#858585ᴄʀᴇᴀᴛᴇᴅ: #FFFFFF" + sdf.format(new Date(order.getCreationTime())),
                                "#858585ᴇxᴘɪʀᴇs: #FFFFFF" + sdf.format(new Date(order.getExpirationTime())),
                                "",
                                "#858585ᴛɪᴍᴇ ʟᴇꜰᴛ: " + (order.isInDeletionThreshold()
                                        ? "#FC0000Expired"
                                        : "#00FC00" + TimeUtil.formatTimeConcise(order.getRemainingTime()))
                        ))
                .consumer(event -> {}));

        this.addButton(25, new InventoryButton()
                .creator(p -> {
                    Material mat;
                    String statusText;
                    String statusDesc;

                    if (order.isComplete()) {
                        mat = XUtil.crossMaterial("LIME_DYE");
                        statusText = "#00FC00Complete";
                        statusDesc = "#858585All items delivered";
                    } else if (order.isExpired()) {
                        mat = XUtil.crossMaterial("RED_DYE");
                        statusText = "#FC0000Pending Deletion";
                        statusDesc = "#858585Past deletion threshold";
                    } else if (order.isInDeletionThreshold()) {
                        mat = XUtil.crossMaterial("ORANGE_DYE");
                        statusText = "#FFA500Expired";
                        statusDesc = "#858585Awaiting cleanup";
                    } else {
                        mat = XUtil.crossMaterial("YELLOW_DYE");
                        statusText = "#FFD700Active";
                        statusDesc = "#858585Accepting deliveries";
                    }

                    return new ItemBuilder(mat)
                            .name("#858585sᴛᴀᴛᴜs")
                            .lore(player, "", statusText, statusDesc);
                })
                .consumer(event -> {}));

        this.addButton(40, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("BARRIER"))
                        .name("#FC0000ᴅᴇʟᴇᴛᴇ ᴏʀᴅᴇʀ")
                        .lore(
                                player,
                                "",
                                "#858585Permanently remove",
                                "#FC0000⚠ Cannot be undone"
                        ))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrderConfirmGUI(order, filter, sort, search, returnPage),
                            player
                    );
                }));

        this.addButton(36, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("ARROW"))
                        .name("#FC0000← ʙᴀᴄᴋ")
                        .lore(player, "#858585Return to orders"))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(filter, sort, search, returnPage),
                            player
                    );
                }));

        ItemBuilder filler = new ItemBuilder(XUtil.crossMaterial("GRAY_STAINED_GLASS_PANE")).name(" ");

        for (int i = 0; i < 45; i++) {
            if (getInventory().getItem(i) != null)
                continue;

            getInventory().setItem(i, filler);
        }

        super.decorate(player);
    }

    private String getItemName(
            Order order
    ) {
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

    private String createProgressBar(
            double percentage
    ) {
        int filled = (int) (percentage / 10);
        StringBuilder bar = new StringBuilder();

        for (int i = 0; i < 10; i++)
            bar.append(i < filled ? "#00FC00▌" : "#858585▌");

        return bar.toString();
    }
}