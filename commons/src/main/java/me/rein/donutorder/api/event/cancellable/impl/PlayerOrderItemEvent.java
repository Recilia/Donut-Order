package me.rein.donutorder.api.event.cancellable.impl;

import lombok.Getter;
import me.rein.donutorder.api.event.cancellable.CancellableEvent;
import me.rein.donutorder.api.util.Order;
import org.bukkit.entity.Player;

@Getter
public class PlayerOrderItemEvent extends CancellableEvent {

    private final Player player;
    private final Order order;

    public PlayerOrderItemEvent(
            Player player,
            Order order
    ) {
        this.player = player;
        this.order = order;
    }
}
