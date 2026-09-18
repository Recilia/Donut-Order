package me.rein.donutorder.listener;

import me.rein.donutorder.commons.database.DatabaseManager;
import me.rein.donutorder.commons.enums.Notifications;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.scheduler.OScheduler;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.List;

public class UnclaimedOrdersCheckEvent implements Listener {
    @EventHandler
    public void checkOrdersForNotification(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        OScheduler.async().run(() -> {
            List<Order> orders = DatabaseManager.getDatabase().getUnclaimedOrders(player.getUniqueId());
            if (orders.isEmpty())
                return;

            OScheduler.sync().runAtEntityLater(() -> {
                Notifications.UNCLAIMED_ORDER.push(player,
                        "{amount}", orders.size() + ""
                );
            }, player, 20L * 5);
        });
    }
}
