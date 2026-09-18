package me.rein.donutorder.inventory.impl.admin;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.math.NumberUtil;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

public class AdminOrderConfirmGUI extends InventoryGUI {

    private final Order order;
    private final Filter filter;
    private final SortOrders sort;
    private final String search;
    private final int returnPage;

    public AdminOrderConfirmGUI(
            Order order,
            Filter filter,
            SortOrders sort,
            String search,
            int returnPage
    ) {
        super(
                "ᴀᴅᴍɪɴ → Confirm Deletion"
        );

        this.order = order;
        this.filter = filter;
        this.sort = sort;
        this.search = search;
        this.returnPage = returnPage;
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, super.title);
    }

    @Override
    public void decorate(Player player) {
        String itemName = getItemName(order);

        this.addButton(13, new InventoryButton()
                .creator(p -> new ItemBuilder(order.getItem().clone())
                        .name("#FFA500ᴏʀᴅᴇʀ ᴛᴏ ᴅᴇʟᴇᴛᴇ")
                        .lore(
                                player,
                                "",
                                "#858585ɪᴅ: #FFFFFF" + order.getOrderId().toString().substring(0, 8),
                                "#858585ᴘʟᴀʏᴇʀ: #FFFFFF" + order.getPlayerName(),
                                "#858585ɪᴛᴇᴍ: #FFFFFF" + itemName,
                                "#858585ᴀᴍᴏᴜɴᴛ: #FFFFFF" + NumberUtil.formatPrice(order.getTotalAmount()),
                                "#858585ᴠᴀʟᴜᴇ: #00FC00$" + NumberUtil.formatPrice(order.getTotalCost()),
                                ""
                        ))
                .consumer(event -> {}));

        this.addButton(11, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("LIME_WOOL"))
                        .name("#00FC00✓ ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛɪᴏɴ")
                        .lore(
                                player,
                                "",
                                "#858585Permanently remove this order",
                                "",
                                "#FC0000⚠ This cannot be undone!"
                        ))
                .consumer(event -> {
                    DatabaseManager.getDatabase().deleteOrder(order.getOrderId());
                    OrderManager.getInstance().removeOrder(order.getOrderId());
                    DatabaseManager.getDatabase().logOrderAction(
                            "ADMIN_DELETE",
                            order,
                            "Deleted by " + player.getName()
                    );

                    player.playSound(player.getLocation(), Sound.ENTITY_IRON_GOLEM_DEATH, 0.5f, 1.2f);
                    player.sendMessage(ChatUtil.c(
                            "#FC0000✗ &fOrder #FC0000" + order.getOrderId().toString().substring(0, 8) +
                                    " &f(" + order.getPlayerName() + ") deleted"
                    ));

                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(filter, sort, search, returnPage),
                            player
                    );
                }));

        this.addButton(15, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("RED_WOOL"))
                        .name("#FC0000✗ ᴄᴀɴᴄᴇʟ")
                        .lore(
                                player,
                                "",
                                "#858585Go back without deleting"
                        ))
                .consumer(event -> {
                    player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.0f);
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(filter, sort, search, returnPage),
                            player
                    );
                }));

        ItemBuilder redFiller = new ItemBuilder(XUtil.crossMaterial("RED_STAINED_GLASS_PANE")).name(" ");
        ItemBuilder blackFiller = new ItemBuilder(XUtil.crossMaterial("BLACK_STAINED_GLASS_PANE")).name(" ");

        for (int i = 0; i < 27; i++) {
            if (getInventory().getItem(i) != null)
                continue;

            if (i < 9 || i >= 18 || i % 9 == 0 || i % 9 == 8) {
                getInventory().setItem(i, redFiller);
                continue;
            }

            getInventory().setItem(i, blackFiller);
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

    // yes yes ik this method is used in another classes
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
}