package me.rein.donutorder.commands.args;

import me.rein.donutorder.commons.util.message.ChatUtil;
import org.bukkit.command.CommandSender;

import java.util.List;

public interface CommandArgs {
    boolean execute(CommandSender sender, String label, String[] args);
    List<String> tabComplete(CommandSender sender, String[] args);

    default void onlyPlayers(CommandSender sender) {
        sender.sendMessage(ChatUtil.c("&cOnly players can use this command!"));
    }
}
