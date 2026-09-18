package me.rein.donutorder.listener;

import me.rein.donutorder.api.currency.CurrencyManager;
import me.rein.donutorder.api.event.OrderExpirationEvent;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.manager.OrderManager;
import me.rein.donutorder.commons.manager.WebhookManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class ExpirationOrderEvent implements Listener {

    @EventHandler
    public void orderExpired(OrderExpirationEvent orderExpirationEvent) {
        Order order = orderExpirationEvent.getOrder();

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(order.getCreatorId());

        if (OrderManager.getInstance().isOrderFull(order))
            return;

        double moneyToBeRefunded = order.getTotalCost() - order.getDeliveredAmount() * order.getPricePerItem();

        if (Double.isNaN(moneyToBeRefunded) || Double.isInfinite(moneyToBeRefunded) || moneyToBeRefunded <= 0)
            return;

        CurrencyManager.getInstance().deposit(offlinePlayer, moneyToBeRefunded);

        WebhookManager.getInstance().logOrderExpire(order, moneyToBeRefunded);
    }
}
