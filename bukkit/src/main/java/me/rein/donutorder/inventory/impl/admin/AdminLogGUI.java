package me.rein.donutorder.inventory.impl.admin;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.objectholders.OrderLogEntry;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.xseries.XUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.List;
import java.util.UUID;

public class AdminLogGUI extends InventoryGUI {

    private final int page;
    private final int maxPage;
    private final UUID targetPlayer;
    private final String targetName;
    private final List<OrderLogEntry> logs;
    private static final int LOGS_PER_PAGE = 28;

    public AdminLogGUI(
            UUID targetPlayer,
            int page
    ) {
        super(
                targetPlayer != null ?
                    "ᴏʀᴅᴇʀ ʟᴏɢs → Player History" :
                    "ᴏʀᴅᴇʀ ʟᴏɢs → Server History"
        );

        this.targetPlayer = targetPlayer;
        this.page = page;

        if (targetPlayer != null) {
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(targetPlayer);
            this.targetName = offlinePlayer.getName() != null ? offlinePlayer.getName() : "Unknown";
            this.logs = DatabaseManager.getDatabase().getPlayerHistory(targetPlayer, 9999999);
        } else {
            this.targetName = "Server";
            this.logs = DatabaseManager.getDatabase().getRecentHistory(9999999);
        }

        this.maxPage = Math.max(1, (int) Math.ceil((double) logs.size() / LOGS_PER_PAGE));
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 54, this.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        for (int i = 45; i < 54; i++) {
            if (i == 45 || i == 49 || i == 50 || i == 53) continue;
            this.addButton(i, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("GRAY_STAINED_GLASS_PANE"))
                            .name(" "))
                    .consumer(event -> {}));
        }

        if (targetPlayer != null) {
            ItemStack skull = new ItemStack(XUtil.crossMaterial("PLAYER_HEAD"));
            SkullMeta meta = (SkullMeta) skull.getItemMeta();
            meta.setOwningPlayer(Bukkit.getOfflinePlayer(targetPlayer));
            skull.setItemMeta(meta);

            this.addButton(4, new InventoryButton()
                    .creator(p -> new ItemBuilder(skull)
                            .name("#E63A35" + targetName + "'s ʟᴏɢs")
                            .lore(
                                    player,
                                    "#E63A35" + logs.size() + " &ftotal entries",
                                    "#E63A35Page " + page + "&f/" + maxPage,
                                    "",
                                    "&7Showing order history for",
                                    "&7" + targetName
                            ))
                    .consumer(event -> {}));
        } else {
            this.addButton(4, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("BEACON"))
                            .name("#E63A35sᴇʀᴠᴇʀ ʟᴏɢs")
                            .lore(
                                    player,
                                    "#E63A35" + logs.size() + " &ftotal entries",
                                    "#E63A35Page " + page + "&f/" + maxPage,
                                    "",
                                    "&7Showing all server orders"
                            ))
                    .consumer(event -> {}));
        }

        int startIndex = (page - 1) * LOGS_PER_PAGE;
        int endIndex = Math.min(startIndex + LOGS_PER_PAGE, logs.size());

        int slot = 10;
        for (int i = startIndex; i < endIndex; i++) {
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;

            OrderLogEntry log = logs.get(i);
            Material material = getMaterialForAction(log.getAction());
            String actionColor = getColorForAction(log.getAction());
            String actionName = getFormattedAction(log.getAction());

            this.addButton(slot, new InventoryButton()
                    .creator(p -> new ItemBuilder(material)
                            .name(actionColor + actionName)
                            .lore(
                                    player,
                                    "#E63A35" + log.getFormattedTime(),
                                    "",
                                    "#E63A35Player: &f" + (log.getPlayerName() != null ? log.getPlayerName() : "Unknown"),
                                    "#E63A35Item: &f" + (log.getItemType() != null ? formatItemName(log.getItemType()) : "N/A"),
                                    "#E63A35Amount: &f" + log.getAmount() + " items",
                                    "#E63A35Price: &f$" + String.format("%.2f", log.getPrice()) + " each",
                                    "",
                                    "&7" + log.getDetails(),
                                    "",
                                    "#858585ID: " + log.getOrderId().toString().substring(0, 8)
                            ))
                    .consumer(event -> {}));

            slot++;
        }

        if (page > 1) {
            this.addButton(45, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("ARROW"))
                            .name("#E63A35ʙᴀᴄᴋ")
                            .lore(
                                    player,
                                    "&fClick to go to page " + (page - 1)
                            ))
                    .consumer(event -> {
                        DonutOrder.getGUIManager().openGUI(new AdminLogGUI(targetPlayer, page - 1), player);
                    }));
        }

        if (page < maxPage) {
            this.addButton(53, new InventoryButton()
                    .creator(p -> new ItemBuilder(XUtil.crossMaterial("ARROW"))
                            .name("#E63A35ɴᴇxᴛ")
                            .lore(
                                    player,
                                    "&fClick to go to page " + (page + 1)
                            ))
                    .consumer(event -> {
                        DonutOrder.getGUIManager().openGUI(new AdminLogGUI(targetPlayer, page + 1), player);
                    }));
        }

        this.addButton(49, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("BARRIER"))
                        .name("#FC0000ᴄʟᴏsᴇ")
                        .lore(
                                player,
                                "&fClick to return to admin panel"
                        ))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new AdminGUI(), player);
                }));

        this.addButton(50, new InventoryButton()
                .creator(p -> new ItemBuilder(targetPlayer != null ? XUtil.crossMaterial("BEACON") : XUtil.crossMaterial("PLAYER_HEAD"))
                        .name("#E63A35sᴡɪᴛᴄʜ ᴠɪᴇᴡ")
                        .lore(
                                player,
                                "&fCurrently viewing: " + (targetPlayer != null ? "#E63A35Player" : "#E63A35Server"),
                                "",
                                "&fClick to switch to " + (targetPlayer != null ? "server logs" : "player logs")
                        ))
                .consumer(event -> {
                    if (targetPlayer != null) {
                        DonutOrder.getGUIManager().openGUI(new AdminLogGUI(null, 1), player);
                    } else {
                        player.sendMessage("§cUse /order admin history <player> to view specific player logs");
                    }
                }));

        super.decorate(player);
    }

    private Material getMaterialForAction(String action) {
        return switch (action) {
            case "CREATE" -> XUtil.crossMaterial("EMERALD");
            case "DELETE" -> XUtil.crossMaterial("REDSTONE");
            case "EXPIRE" -> XUtil.crossMaterial("COAL");
            default -> XUtil.crossMaterial("IRON_INGOT");
        };
    }

    private String getColorForAction(String action) {
        return switch (action) {
            case "CREATE" -> "#00FC00";
            case "DELETE" -> "#FC0000";
            case "EXPIRE" -> "#858585";
            default -> "#E63A35";
        };
    }

    private String getFormattedAction(String action) {
        return switch (action) {
            case "CREATE" -> "ᴏʀᴅᴇʀ ᴄʀᴇᴀᴛᴇᴅ";
            case "DELETE" -> "ᴏʀᴅᴇʀ ᴅᴇʟᴇᴛᴇᴅ";
            case "EXPIRE" -> "ᴏʀᴅᴇʀ ᴇxᴘɪʀᴇᴅ";
            default -> action;
        };
    }

    private String formatItemName(String itemType) {
        if (itemType == null) return "Unknown";
        return itemType.toUpperCase().replace("_", " ");
    }
}