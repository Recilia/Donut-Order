package me.rein.donutorder.api.event;

import lombok.Getter;
import me.rein.donutorder.api.util.Order;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class OrderExpirationEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Order order;

    public OrderExpirationEvent(
            Order order
    ) {
        this.order = order;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
