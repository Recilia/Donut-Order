package me.rein.donutorder.commons.util.message;

import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.entity.Player;

public class ActionBarUtils {

    public static void sendActionBarMessage(
            Player player,
            String message
    ) {
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatUtil.c(message)));
    }
}
