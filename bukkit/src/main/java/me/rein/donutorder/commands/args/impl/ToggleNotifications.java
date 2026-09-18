package me.rein.donutorder.commands.args.impl;

import me.rein.donutorder.commands.args.CommandArgs;
import me.rein.donutorder.commons.enums.Notifications;
import me.rein.donutorder.commons.manager.ValuesManager;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.*;

public class ToggleNotifications implements CommandArgs {

    @Override
    public boolean execute(
            CommandSender sender,
            String label,
            String[] args
    ) {
        if (!ValuesManager.getInstance().isNotificationsToggle())
            return false;

        if (!(sender instanceof Player player)) {
            onlyPlayers(sender);
            return false;
        }

        Notifications.toggleNotifications(player);

        return true;
    }

    @Override
    public List<String> tabComplete(
            CommandSender sender,
            String[] args
    ) {
        return Collections.emptyList();
    }
}
