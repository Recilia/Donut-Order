package me.rein.donutorder.inventory;

import lombok.Getter;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import me.rein.donutorder.commons.util.sound.SoundWrapper;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;

import java.util.function.Consumer;
import java.util.function.Function;

@Getter
public class InventoryButton {

    private Function<Player, ItemBuilder> iconCreator;
    private Consumer<InventoryClickEvent> eventConsumer;
    private boolean async = false; // creator
    private boolean asyncConsumer = false; // consumer
    private SoundWrapper sound;

    public InventoryButton creator(
            Function<Player, ItemBuilder> iconCreator
    ) {
        this.iconCreator = iconCreator;
        return this;
    }

    public InventoryButton consumer(
            Consumer<InventoryClickEvent> eventConsumer
    ) {
        this.eventConsumer = eventConsumer;
        return this;
    }

    public InventoryButton async(
            boolean async
    ) {
        this.async = async;
        return this;
    }

    public InventoryButton asyncConsumer(
            boolean asyncConsumer
    ) {
        this.asyncConsumer = asyncConsumer;
        return this;
    }

    public InventoryButton sound(
            SoundWrapper sound
    ) {
        this.sound = sound;
        return this;
    }

    public Consumer<InventoryClickEvent> getEventConsumer() {
        return eventConsumer != null ? e -> {
            if (sound != null)
                sound.play((Player) e.getWhoClicked());

            if (asyncConsumer) {
                OScheduler.async().run(() -> eventConsumer.accept(e));
            } else {
                eventConsumer.accept(e);
            }
        } : e -> {};
    }
}
