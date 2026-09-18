package me.rein.donutorder.api.event;

import lombok.Getter;
import me.rein.donutorder.api.util.Order;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

@Getter
public class PlayerOrderDeliveryCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Order order;

    public PlayerOrderDeliveryCompleteEvent(
            Player player,
            Order order
    ) {
        this.player = player;
        this.order = order;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }
}
