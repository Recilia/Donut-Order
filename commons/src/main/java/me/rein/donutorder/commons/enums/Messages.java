package me.rein.donutorder.commons.enums;

import lombok.Getter;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.scheduler.task.Task;
import me.rein.donutorder.commons.util.message.ActionBarUtils;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.objectholders.Replaceable;
import me.rein.donutorder.commons.objectholders.TranslationPlaceholder;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

import static org.bukkit.Bukkit.getLogger;

public enum Messages {
    SELECT_ITEM("select_item"),
    ON_ORDER_PLACED("on_order_placed"),
    ON_DELIVER("on_deliver"),
    ORDER_CANCELLED("order_cancelled", "&aYour order has been successfully cancelled."),
    ORDER_CANCEL_HAS_ITEMS("order_cancel_has_items", "&cYou must collect your items before cancelling this order."),
    NOT_ENOUGH_MONEY("not_enough_money", "&cYou don't have enough money to place an order!"),
    ORDER_ANNOUNCEMENT("order_announcement",
            "&c{player} &fhas ordered &7x{amount} {itemType} &f for &a{priceEach}$&f each, use /order to view it."),
    SELL_ALL("sell_all", "&aYou successfully sold your items for {price}$"),

    NOTIFICATIONS_ITEMS_DELIVERED("notifications.items_delivered"),
    NOTIFICATIONS_UNCLAIMED_ORDER("notifications.unclaimed_order"),
    NOTIFICATIONS_TOGGLE_ON("notifications.toggle.on", "&aOrder notifications enabled!"),
    NOTIFICATIONS_TOGGLE_OFF("notifications.toggle.off", "&cOrder notifications disabled!"),

    PRICING_MIN("pricing.min", "&cYour order must have a minimum total price of {minPrice}"),
    PRICING_MAX("pricing.max", "&cYour order cannot have a higher total price than {maxPrice}"),
    PRICING_PER_UNIT_MIN("pricing.per_unit_min", "&cPrice per item must be at least {minPrice}"),
    PRICING_PER_UNIT_MAX("pricing.per_unit_max", "&cPrice per item cannot be higher than {maxPrice}"),

    PICK_ENCHANTMENTS_UNSELECTED("pick_enchantments.unselected", "&fClick to select"),
    PICK_ENCHANTMENTS_SELECTED("pick_enchantments.selected", "&aSelected"),

    // Signs
    SIGN_AMOUNT("signs.amount"),
    SIGN_PRICE_PER_ITEM("signs.price_per_item"),
    SIGN_SEARCH("signs.search"),

    // Sort orders
    SORT_MOST_PAID("sorts.MOST_PAID"),
    SORT_MOST_DELIVERED("sorts.MOST_DELIVERED"),
    SORT_RECENTLY_LISTED("sorts.RECENTLY_LISTED"),
    SORT_MOST_MONEY_PER_ITEM("sorts.MOST_MONEY_PER_ITEM"),

    // Filters
    FILTER_ALL("filters.ALL"),
    FILTER_BLOCKS("filters.BLOCKS"),
    FILTER_TOOLS("filters.TOOLS"),
    FILTER_FOOD("filters.FOOD"),
    FILTER_COMBAT("filters.COMBAT"),
    FILTER_POTIONS("filters.POTIONS"),
    FILTER_BOOKS("filters.BOOKS"),
    FILTER_INGREDIENTS("filters.INGREDIENTS"),
    FILTER_UTILITIES("filters.UTILITIES"),

    // Sort select
    SORT_SELECT_A_Z("sort_select.A_Z"),
    SORT_SELECT_Z_A("sort_select.Z_A"),
    SORT_SELECT_HIGHEST_PRICE("sort_select.HIGHEST_PRICE"),
    SORT_SELECT_LOWEST_PRICE("sort_select.LOWEST_PRICE"),
    ;

    private final String path;
    @Getter
    private String message;
    private List<String> messageList;

    Messages(String path, String defaultMessage) {
        this.path = path;
        this.message = defaultMessage;
    }

    Messages(String path) {
        this(path, "");
    }

    public List<String> getMessageList() {
        return messageList != null ? messageList : Collections.emptyList();
    }

    public String[] getMessageArray() {
        if (messageList != null)
            return messageList.toArray(new String[0]);

        return new String[]{message};
    }

    public String getSingularFormattedMessage() {
        return ChatUtil.c(message);
    }

    public String getLine(int index) {
        if (messageList != null && index < messageList.size()) {
            return messageList.get(index);
        }
        if (index == 0 && message != null) {
            return message;
        }
        return "";
    }

    public void sendBoth(Player player) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatUtil.c(message));
            ActionBarUtils.sendActionBarMessage(player, message);
        }
    }

    public void sendBoth(Player player, Replaceable replaceable) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(replaceable.replace(message));
            ActionBarUtils.sendActionBarMessage(player, replaceable.replace(message));
        }
    }

    public void sendBoth(Player player, String... placeholders) {
        if (message != null && !message.isEmpty()) {
            Replaceable replaceable = Replaceable.inst(placeholders);
            player.sendMessage(replaceable.replace(message));
            ActionBarUtils.sendActionBarMessage(player, replaceable.replace(message));
        }
    }

    public void sendBoth(Player player, Object... placeholders) {
        if (message == null || message.isEmpty()) return;

        String processedMessage = ChatUtil.c(message);
        Component component = null;

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 >= placeholders.length) break;

            String placeholder = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];

            if (value instanceof TranslationPlaceholder tp) {
                if (component == null) {
                    component = LegacyComponentSerializer.legacy('§').deserialize(processedMessage);
                /*Component.text(processedMessage);*/
                }

                component = component.replaceText(builder ->
                        builder.match(Pattern.quote(placeholder))
                                .replacement(tp.getComponent()));
            } else {
                processedMessage = processedMessage.replace(
                        placeholder, String.valueOf(value));
            }
        }

        if (component != null) {
            player.sendMessage(component);
            player.sendActionBar(component);
        } else {
            player.sendMessage(processedMessage);
            ActionBarUtils.sendActionBarMessage(player, processedMessage);
        }
    }

    public void send(CommandSender player) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatUtil.c(message));
        }
    }

    public void send(CommandSender player, Replaceable replaceable) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(replaceable.replace(message));
        }
    }

    public void send(CommandSender player, String... placeholders) {
        if (message != null && !message.isEmpty()) {
            Replaceable replaceable = Replaceable.inst(placeholders);
            player.sendMessage(replaceable.replace(message));
        }
    }

    public void send(Player player) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(ChatUtil.c(message));
        }
    }

    public void send(Player player, Replaceable replaceable) {
        if (message != null && !message.isEmpty()) {
            player.sendMessage(replaceable.replace(message));
        }
    }

    public void send(Player player, String... placeholders) {
        if (message != null && !message.isEmpty()) {
            Replaceable replaceable = Replaceable.inst(placeholders);
            player.sendMessage(replaceable.replace(message));
        }
    }

    public void send(Player player, Object... placeholders) {
        if (message == null || message.isEmpty()) return;

        String processedMessage = ChatUtil.c(message);
        Component component = null;

        for (int i = 0; i < placeholders.length; i += 2) {
            if (i + 1 >= placeholders.length) break;

            String placeholder = String.valueOf(placeholders[i]);
            Object value = placeholders[i + 1];

            if (value instanceof TranslationPlaceholder tp) {
                if (component == null) {
                    component = LegacyComponentSerializer.legacy('§').deserialize(processedMessage);
                    /*Component.text(processedMessage);*/
                }

                component = component.replaceText(builder ->
                        builder.match(Pattern.quote(placeholder))
                                .replacement(tp.getComponent()));
            } else {
                processedMessage = processedMessage.replace(
                        placeholder, String.valueOf(value));
            }
        }

        if (component != null) {
            player.sendMessage(component);
        } else {
            player.sendMessage(processedMessage);
        }
    }

    public void sendActionBar(Player player) {
        if (message != null && !message.isEmpty()) {
            ActionBarUtils.sendActionBarMessage(player, ChatUtil.c(message));
        }
    }

    public void sendActionBar(Player player, Replaceable replaceable) {
        if (message != null && !message.isEmpty()) {
            ActionBarUtils.sendActionBarMessage(player, replaceable.replace(message));
        }
    }

    public void sendActionBar(Player player, String... placeholders) {
        if (message != null && !message.isEmpty()) {
            Replaceable replaceable = Replaceable.inst(placeholders);
            ActionBarUtils.sendActionBarMessage(player, replaceable.replace(message));
        }
    }

    public void sendWithDelay(Player player, String[] placeholders, String... progressMessages) {
        if (message == null || message.isEmpty()) {
            return;
        }

        final String[] preformatted = new String[progressMessages.length];
        for (int i = 0; i < progressMessages.length; i++) {
            preformatted[i] = ChatUtil.c(progressMessages[i]);
        }

        final int[] step = {0};
        final Task[] taskHolder = {null};
        taskHolder[0] = OScheduler.sync().runRepeating(() -> {
            if (!player.isOnline()) {
                taskHolder[0].cancel();
                return;
            }

            if (step[0] < preformatted.length) {
                ActionBarUtils.sendActionBarMessage(player, preformatted[step[0]]);
            } else {
                sendBoth(player, placeholders);
                taskHolder[0].cancel();
            }
            step[0]++;
        }, 2L);
    }

    // Component
    public void sendWithDelay(Player player, Object[] placeholders, String... progressMessages) {
        if (message == null || message.isEmpty()) {
            return;
        }

        final String[] preformatted = new String[progressMessages.length];
        for (int i = 0; i < progressMessages.length; i++) {
            preformatted[i] = ChatUtil.c(progressMessages[i]);
        }

        final int[] step = {0};
        final Task[] taskHolder = {null};
        taskHolder[0] = OScheduler.sync().runRepeating(() -> {
            if (!player.isOnline()) {
                taskHolder[0].cancel();
                return;
            }

            if (step[0] < preformatted.length) {
                ActionBarUtils.sendActionBarMessage(player, preformatted[step[0]]);
            } else {
                sendBoth(player, placeholders);
                taskHolder[0].cancel();
            }
            step[0]++;
        }, 2L);
    }

    public static void load(ConfigSelector config) {
        config.reload();
        int missingMessages = 0;
        boolean configChanged = false;

        for (Messages value : values()) {
            if (config.getConfig().get(value.path) == null) {
                missingMessages++;
                getLogger().info("[DonutOrder] Adding missing message: " + value.path);

                if (value.messageList != null && value.messageList.size() > 1) {
                    config.getConfig().set(value.path, value.messageList);
                } else {
                    config.getConfig().set(value.path, value.message != null ? value.message : "");
                }
                configChanged = true;
            } else {
                List<String> configMessageList = config.getConfig().getStringList(value.path);
                if (!configMessageList.isEmpty() && configMessageList.size() > 1) {
                    value.messageList = configMessageList;
                    value.message = null;
                } else {
                    String configMessage = config.getConfig().getString(value.path);
                    if (configMessage != null) {
                        value.message = configMessage;
                        value.messageList = null;
                    }
                }
            }
        }

        if (configChanged) {
            try {
                config.saveConfig();
                getLogger().info("[DonutOrder] Added " + missingMessages + " missing messages to config.");
            } catch (Exception e) {
                getLogger().severe("[DonutOrder] Failed to save config after adding missing messages: " + e.getMessage());
            }
        }
    }
}
