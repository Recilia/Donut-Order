package me.rein.donutorder.commands.args.impl.admin;

import lombok.Getter;
import lombok.Setter;
import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.commands.args.CommandArgs;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.impl.MainGUI;
import me.rein.donutorder.inventory.impl.admin.AdminGUI;
import me.rein.donutorder.inventory.impl.admin.AdminLogGUI;
import me.rein.donutorder.commons.manager.MaterialManager;
import me.rein.donutorder.commons.manager.price.PriceManager;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.debug.Timer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class Admin implements CommandArgs {
    public static @Getter @Setter boolean isLocked = false;
    private static @Getter Set<UUID> debugMode = new HashSet<>();

    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("order.admin")) {
            return false;
        }

        if (args.length < 2) {
            if (sender instanceof Player console) {
                DonutOrder.getGUIManager().openGUI(new AdminGUI(), console);
                return true;
            }

            sender.sendMessage(ChatUtil.c("&cUsage: /order admin <reload/add> (add)<price>"));

            return false;
        }

        String command = args[1];

        switch (command.toLowerCase()) {
            case "add" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatUtil.c("&cOnly players can use this command!"));
                    return false;
                }

                if (args.length < 3) {
                    sender.sendMessage(ChatUtil.c("&cUsage: /order admin add <price>"));
                    return false;
                }

                String priceStr = args[2];
                double price;
                try {
                    price = Double.parseDouble(priceStr);
                    if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
                        sender.sendMessage(ChatUtil.c("&cInvalid price!"));
                        return false;
                    }
                } catch (Exception ignored) {
                    sender.sendMessage(ChatUtil.c("&cInvalid price!"));
                    return false;
                }

                ItemStack itemStack = player.getInventory().getItemInHand();

                if (itemStack.getType() == Material.AIR) {
                    sender.sendMessage(ChatUtil.c("&cInvalid item!"));
                    return false;
                }

                ItemStack clonedItemStack = itemStack.clone();
                clonedItemStack.setAmount(1);

                PriceManager.getInstance().addOrUpdatePrice(clonedItemStack, price);
                MaterialManager.getInstance().getMaterials()
                        .put(clonedItemStack, new MaterialManager.MaterialData(
                                clonedItemStack,
                                MaterialManager.getInstance().getCategory(clonedItemStack),
                                (int) price
                        ));

                sender.sendMessage(ChatUtil.c("&aItem added!"));
            }

            case "reload" -> {
                Timer timer = new Timer().start();

                Arrays.stream(ConfigSelector.values()).toList()
                        .forEach(ConfigSelector::reload);

                DonutOrder.getInstance().load();

                sender.sendMessage(ChatUtil.c("&aReloaded all .yml files successfully in " + timer.get() + "ms!"));
            }

            case "opengui" -> {
                if (args.length < 3) {
                    sender.sendMessage(ChatUtil.c("&cUsage: /order admin opengui <username>"));
                    return false;
                }

                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(ChatUtil.c("&cPlayer not found!"));
                    return false;
                }

                /*DonutOrder.getGUIManager().openGUI(new MainGUI(
                                Filter.ALL,
                                SortOrders.MOST_PAID,
                                "",
                                1,
                                Collections.emptyList()
                        ),
                        target
                );*/
                MainGUI.open(target);

                sender.sendMessage(ChatUtil.c("&aForce opened order gui for " + target.getName()));
            }

            case "lock" -> {
                isLocked = !isLocked;

                sender.sendMessage(ChatUtil.c(isLocked ? "&cLockdown activated." : "&cLockdown deactivated."));
            }

            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatUtil.c("&cOnly players can use this command!"));
                    return false;
                }

                boolean toggle = !debugMode.contains(player.getUniqueId());

                if (toggle)
                    debugMode.add(player.getUniqueId());
                else
                    debugMode.remove(player.getUniqueId());

                sender.sendMessage(ChatUtil.c("&aDebug turned " + (toggle ? "ON" : "OFF")));
            }

            case "history" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatUtil.c("&cOnly players can use this command!"));
                    return false;
                }

                if (args.length < 3) {
                    DonutOrder.getGUIManager().openGUI(new AdminLogGUI(null, 1), player);
                    return true;
                }

                String targetName = args[2];
                OfflinePlayer target = Bukkit.getOfflinePlayer(targetName);

                if (!target.hasPlayedBefore()) {
                    sender.sendMessage(ChatUtil.c("&cPlayer '" + targetName + "' not found!"));
                    return false;
                }

                DonutOrder.getGUIManager().openGUI(new AdminLogGUI(target.getUniqueId(), 1), player);
                sender.sendMessage(ChatUtil.c("&aOpening order history for " + target.getName()));
            }
        }

        return false;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (!sender.hasPermission("order.admin")) {
            return Collections.emptyList();
        }

        List<String> list = new ArrayList<>();

        switch (args.length) {
            case 2 -> {
                list.add("reload");
                list.add("add");
                list.add("lock");
                list.add("debug");
                list.add("history");
            }

            case 3 -> {
                switch (args[1].toLowerCase()) {
                    case "add" -> list.add("<price>");
                    case "history" -> {
                        for (Player p : Bukkit.getOnlinePlayers())
                            list.add(p.getName());
                    }
                }
            }
        }

        return list.stream()
                .filter(s -> s.startsWith(args[args.length - 1]))
                .toList();
    }
}
