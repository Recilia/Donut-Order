package me.rein.donutorder.commands;

import me.rein.donutorder.DonutOrder;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commands.args.Args;
import me.rein.donutorder.commands.args.impl.admin.Admin;
import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.order.Filter;
import me.rein.donutorder.commons.enums.order.SortOrders;
import me.rein.donutorder.inventory.impl.MainGUI;
import me.rein.donutorder.inventory.impl.NewOrderGUI;
import me.rein.donutorder.commons.manager.MaterialManager;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.manager.PermissionManager;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class OrderCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("order.use")/* || !sender.isOp()*/)
            return false;

        if (args.length == 0) {
            if (!(sender instanceof Player)) {
                return false;
            }

            Player player = (Player) sender;

            if (Admin.isLocked) {
                sender.sendMessage(ChatUtil.c("&cOrder is in lockdown."));
                return false;
            }

            MainGUI.open(player);

            return true;
        }

        String subCommand = args[0].toLowerCase();

        OfflinePlayer target = Bukkit.getPlayer(subCommand);

        if (target != null) {
            if (!(sender instanceof Player)) {
                return false;
            }

            Player player = (Player) sender;

            UUID uuid = target.getUniqueId();

            if (Admin.isLocked) {
                sender.sendMessage(ChatUtil.c("&cOrder is in lockdown."));
                return false;
            }

            OScheduler.async().run(() -> {
                List<Order> orders = DatabaseManager.getDatabase().getPlayerOrders(uuid)
                        .stream()
                        .filter(order -> !OrderManager.getInstance().isOrderFull(order))
                        .collect(Collectors.toList());

                if (orders.isEmpty())
                    return;

                MainGUI.open(player);
            });

            return true;
        }

        Material material = Material.getMaterial(subCommand.toUpperCase());
        if (material != null) {
            if (!(sender instanceof Player)) {
                return false;
            }

            Player player = (Player) sender;

            if (Admin.isLocked) {
                sender.sendMessage(ChatUtil.c("&cOrder is in lockdown."));
                return false;
            }

            if (ValuesManager.getInstance().isQuickSearch()) {
                MainGUI.open(player, Filter.ALL, SortOrders.MOST_PAID, subCommand, 1);
                return true;
            }

            if (MaterialManager.getInstance().hasPricedMaterial(material)) {
                OScheduler.async().run(() -> {
                    List<Order> orders = DatabaseManager.getDatabase()
                            .getPlayerOrders(player.getUniqueId())
                            .stream()
                            .filter(order -> !OrderManager.getInstance().isOrderFull(order))
                            .collect(Collectors.toList());

                    OScheduler.sync().runAtEntity(() -> {
                        int orderSize = orders.size();

                        if (orderSize < PermissionManager.getInstance().getMaxOrdersForPlayer(player)) {
                            DonutOrder.getGUIManager().openGUI(
                                    new NewOrderGUI(
                                            1,
                                            0,
                                            true,
                                            new ItemBuilder(material)
                                    ),
                                    player)
                            ;
                        }
                    }, player);
                });

                return true;
            }
        }

        Args argHandler = Args.fromString(subCommand);
        if (argHandler == null) {
            if (!(sender instanceof Player player))
                return false;

            if (Admin.isLocked) {
                sender.sendMessage(ChatUtil.c("&cOrder is in lockdown."));
                return false;
            }

            if (ValuesManager.getInstance().isQuickSearch()) {
                String searchTerm = String.join(" ", args);
                MainGUI.open(player, Filter.ALL, SortOrders.MOST_PAID, searchTerm, 1);
            } else {
                MainGUI.open(player);
            }

            return false;
        }

        return argHandler.get().execute(sender, label, args);
    }

    public static class OrderCommandTabCompleter implements TabCompleter {
        @Override
        public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
            if (args.length == 1) {
                List<String> completions = Arrays.stream(Args.values())
                        .filter(arg -> arg != Args.ADMIN || sender.hasPermission("order.admin"))
                        .map(arg -> arg.name().toLowerCase())
                        .collect(Collectors.toList());

                return filterCompletions(completions, args[0]);
            }

            try {
                Args argHandler = Args.valueOf(args[0].toUpperCase());

                if (argHandler == Args.ADMIN && !sender.hasPermission("order.admin")) {
                    return Collections.emptyList();
                }

                return filterCompletions(
                        argHandler.get().tabComplete(sender, args),
                        args[args.length - 1]
                );
            } catch (Exception ignored) {
                return Collections.emptyList();
            }
        }

        private List<String> filterCompletions(List<String> completions, String prefix) {
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(prefix.toLowerCase()))
                    .collect(Collectors.toList());
        }
    }
}
