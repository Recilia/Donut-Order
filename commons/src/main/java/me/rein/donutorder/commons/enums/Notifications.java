package me.rein.donutorder.commons.enums;

import lombok.Getter;
import me.rein.donutorder.api.event.cancellable.impl.OrderNotificationEvent;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.Replaceable;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public enum Notifications {
    ITEMS_DELIVERED(Messages.NOTIFICATIONS_ITEMS_DELIVERED),
    UNCLAIMED_ORDER(Messages.NOTIFICATIONS_UNCLAIMED_ORDER),
    ;

    @Getter private final Messages message;
    private static final Set<UUID> notificationsDisabled = ConcurrentHashMap.newKeySet();

    public static boolean hasNotificationsDisabled(
            Player player
    ) {
        return notificationsDisabled.contains(player.getUniqueId());
    }

    public static void toggleNotifications(
            Player player
    ) {
        UUID playerUUID = player.getUniqueId();

        if (notificationsDisabled.contains(playerUUID)) {
            notificationsDisabled.remove(playerUUID);
            Messages.NOTIFICATIONS_TOGGLE_ON.sendBoth(player);
            return;
        }

        notificationsDisabled.add(playerUUID);
        Messages.NOTIFICATIONS_TOGGLE_OFF.sendBoth(player);
    }

    Notifications(
            Messages message
    ) {
        this.message = message;
    }

    public boolean areNotificationsCancelled(
            Player player

    ) {
        if (!ValuesManager.getInstance().isNotificationsEnabled())
            return true;

        if (hasNotificationsDisabled(player))
            return true;

        OrderNotificationEvent event = new OrderNotificationEvent(player, message.getSingularFormattedMessage());

        Bukkit.getPluginManager().callEvent(event);

        return event.isCancelled();
    }

    public void push(
            Player player
    ) {
        if (areNotificationsCancelled(player))
            return;

        message.sendBoth(player);
    }

    public void push(
            Player player,
            String... placeholders
    ) {
        if (areNotificationsCancelled(player))
            return;

        message.sendBoth(player, placeholders);
    }

    // Component
    public void push(
            Player player,
            Object... placeholders
    ) {
        if (areNotificationsCancelled(player))
            return;

        message.sendBoth(player, placeholders);
    }

    public void push(
            Player player,
            Replaceable replaceable
    ) {
        if (areNotificationsCancelled(player))
            return;

        message.sendBoth(player, replaceable);
    }
}
