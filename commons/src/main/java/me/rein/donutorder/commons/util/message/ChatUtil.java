package me.rein.donutorder.commons.util.message;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatUtil {

    public static final Pattern HEX_PATTERN = Pattern.compile("#([A-Fa-f0-9]{6})");

    public static String c(
            String textToTranslate
    ) {
        Matcher matcher = HEX_PATTERN.matcher(textToTranslate);
        StringBuffer buffer = new StringBuffer();

        while (matcher.find())
            matcher.appendReplacement(buffer, ChatColor.of("#" + matcher.group(1)).toString());

        return ChatColor.translateAlternateColorCodes('&', matcher.appendTail(buffer).toString());
    }

    public static List<String> c(List<String> stringList) {
        return stringList.stream().map(ChatUtil::c).toList();
    }

    public static String[] c(String[] strings) {
        return Arrays.stream(strings).map(ChatUtil::c).toArray(String[]::new);
    }
}
