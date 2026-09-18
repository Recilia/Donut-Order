package me.rein.donutorder.inventory.impl.admin;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commands.args.impl.admin.Admin;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.commons.util.xseries.XUtil;
import me.rein.donutorder.inventory.InventoryButton;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.debug.Timer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;

import java.util.Arrays;

public class AdminGUI extends InventoryGUI {
    public AdminGUI() {
        super(
                "ᴀᴅᴍɪɴ → Control Panel"
        );
    }

    @Override
    protected Inventory createInventory() {
        return Bukkit.createInventory(null, 27, this.title);
    }

    @Override
    public void decorate(
            Player player
    ) {
        this.addButton(10, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("COMMAND_BLOCK"))
                        .name("#00B4DBʀᴇʟᴏᴀᴅ")
                        .lore(
                                player,
                                "&fRefresh configurations",
                                "#858585~50-200ms"
                        ))
                .consumer(event -> {
                    getInventory().close();
                    Timer timer = new Timer().start();
                    Arrays.stream(ConfigSelector.values()).toList()
                            .forEach(ConfigSelector::reload);
                    DonutOrder.getInstance().load();
                    player.sendMessage(ChatUtil.c("#E63A35✓ &fReloaded in #E63A35" + timer.get() + "ms"));
                })
        );

        this.addButton(11, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("EMERALD"))
                        .name("#00FC00ᴀᴅᴅ ɪᴛᴇᴍ")
                        .lore(
                                player,
                                "&fAdd to order system",
                                "#858585Hold item & set price"
                        ))
                .consumer(event -> {
                    player.sendMessage(ChatUtil.c("#E63A35Type the price in chat"));
                    AdminListener.getAddItem().add(player.getUniqueId());
                    getInventory().close();
                })
        );

        boolean isLocked = Admin.isLocked();
        this.addButton(12, new InventoryButton()
                .creator(p -> new ItemBuilder(isLocked ? XUtil.crossMaterial("BARRIER")
                        : XUtil.crossMaterial("IRON_DOOR"))
                        .name("#FC0000ʟᴏᴄᴋᴅᴏᴡɴ")
                        .lore(
                                player,
                                isLocked ? "#FC0000Currently locked" : "#00FC00Currently open",
                                "#858585Blocks all orders"
                        ))
                .consumer(event -> {
                    Admin.setLocked(!Admin.isLocked());
                    player.sendMessage(ChatUtil.c(Admin.isLocked() ?
                            "#FC0000⚠ &fLockdown activated" :
                            "#00FC00✓ &fLockdown deactivated"));
                    this.decorate(player);
                })
        );

        this.addButton(13, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("CHEST"))
                        .name("#FF6B6Bᴍᴀɴᴀɢᴇ ᴏʀᴅᴇʀs")
                        .lore(
                                player,
                                "&fBrowse & delete orders",
                                "#858585View all active orders"
                        ))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(
                            new AdminOrdersGUI(Filter.ALL, SortOrders.RECENTLY_LISTED, "", 1),
                            player
                    );
                })
        );

        this.addButton(14, new InventoryButton()
                .creator(p -> new ItemBuilder(XUtil.crossMaterial("BOOK"))
                        .name("#FFA500ʜɪsᴛᴏʀʏ")
                        .lore(
                                player,
                                "&fView order logs",
                                "#858585Track all actions"
                        ))
                .consumer(event -> {
                    DonutOrder.getGUIManager().openGUI(new AdminLogGUI(null, 1), player);
                })
        );

        boolean inDebug = Admin.getDebugMode().contains(player.getUniqueId());
        this.addButton(16, new InventoryButton()
                .creator(p -> new ItemBuilder(inDebug ? XUtil.crossMaterial("SPECTRAL_ARROW")
                        : XUtil.crossMaterial("REDSTONE_TORCH"))
                        .name("#DA70D6ᴅᴇʙᴜɢ")
                        .lore(
                                player,
                                inDebug ? "#00FC00Active" : "#858585Inactive",
                                "#858585Enhanced logging"
                        ))
                .consumer(event -> {
                    boolean toggle = !Admin.getDebugMode().contains(player.getUniqueId());
                    if (toggle)
                        Admin.getDebugMode().add(player.getUniqueId());
                    else
                        Admin.getDebugMode().remove(player.getUniqueId());
                    player.sendMessage(ChatUtil.c("#DA70D6✦ &fDebug " +
                            (toggle ? "#00FC00enabled" : "#FC0000disabled")));
                    this.decorate(player);
                })
        );

        ItemBuilder filler = new ItemBuilder(XUtil.crossMaterial("GRAY_STAINED_GLASS_PANE")).name(" ");
        for (int i = 0; i < 27; i++) {
            if (getInventory().getItem(i) == null) {
                getInventory().setItem(i, filler);
            }
        }

        super.decorate(player);
    }
}