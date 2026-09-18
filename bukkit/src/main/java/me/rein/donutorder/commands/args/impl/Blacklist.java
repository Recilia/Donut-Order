package me.rein.donutorder.commands.args.impl;

import me.rein.donutorder.commands.args.CommandArgs;
import me.rein.donutorder.commons.manager.BlacklistManager;
import me.rein.donutorder.commons.util.message.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.List;
import java.util.stream.Collectors;

public class Blacklist implements CommandArgs {
    @Override
    public boolean execute(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(ChatUtil.c("&cUsage: /order blacklist <add/remove> <item>"));
            return true;
        }

        String action = args[1].toLowerCase();
        String itemName = args[2].toUpperCase();
        BlacklistManager blacklistManager = BlacklistManager.getInstance();

        switch (action) {
            case "add" -> {
                if (blacklistManager.isBlacklisted(itemName)) {
                    sender.sendMessage(ChatUtil.c("&cThis item is already blacklisted!"));
                    return true;
                }
                blacklistManager.addItem(itemName);
                sender.sendMessage(ChatUtil.c("&aAdded &f" + itemName + " &ato the blacklist."));
            }
            case "remove" -> {
                if (!blacklistManager.isBlacklisted(itemName)) {
                    sender.sendMessage(ChatUtil.c("&cThis item is not blacklisted!"));
                    return true;
                }
                blacklistManager.removeItem(itemName);
                sender.sendMessage(ChatUtil.c("&aRemoved &f" + itemName + " &afrom the blacklist."));
            }
            default -> {
                sender.sendMessage(ChatUtil.c("&cInvalid action! Use 'add' or 'remove'"));
                return true;
            }
        }
        return true;
    }

    @Override
    public List<String> tabComplete(CommandSender sender, String[] args) {
        if (args.length == 2) {
            List<String> completions = List.of("add", "remove");
            return completions.stream()
                    .filter(s -> s.toLowerCase().startsWith(args[1].toLowerCase()))
                    .collect(Collectors.toList());
        }
        if (args.length == 3 && args[1].equalsIgnoreCase("remove")) {
            return BlacklistManager.getInstance().getBlacklistedItems().stream()
                    .filter(s -> s.toLowerCase().startsWith(args[2].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return List.of();
    }
}
