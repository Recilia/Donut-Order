package me.rein.donutorder.api.event.cancellable.impl;

import lombok.Getter;
import me.rein.donutorder.api.event.cancellable.CancellableEvent;
import me.rein.donutorder.api.util.Order;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Collections;
import java.util.Map;

@Getter
public class PlayerSellAllEvent extends CancellableEvent {

    private final Player player;
    private final Order order;
    private final Map<Integer, ItemStack> items;

    public PlayerSellAllEvent(
            Player player,
            Order order,
            Map<Integer, ItemStack> items
    ) {
        this.player = player;
        this.order = order;
        this.items = Collections.unmodifiableMap(items);
    }
}
