package me.rein.donutorder.api.event.cancellable.impl;

import lombok.AllArgsConstructor;
import lombok.Getter;
import me.rein.donutorder.api.event.cancellable.CancellableEvent;
import org.bukkit.entity.Player;

@Getter
@AllArgsConstructor
public class OrderNotificationEvent extends CancellableEvent {
    private final Player player;
    private final String notification;
}
