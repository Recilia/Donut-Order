package me.rein.donutorder.api.event;

import lombok.Getter;
import me.rein.donutorder.api.shop.ShopHook;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
public class ShopRegistrationCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Map<String, ShopHook> registeredShops;
    private final ShopHook activeShop;

    public ShopRegistrationCompleteEvent(
            Map<String, ShopHook> registeredShops,
            ShopHook activeShop
    ) {
        this.registeredShops = registeredShops;
        this.activeShop = activeShop;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
