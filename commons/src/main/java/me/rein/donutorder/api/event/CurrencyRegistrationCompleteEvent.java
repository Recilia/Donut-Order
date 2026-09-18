package me.rein.donutorder.api.event;

import lombok.Getter;
import me.rein.donutorder.api.currency.CurrencyHook;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@Getter
public class CurrencyRegistrationCompleteEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Map<String, CurrencyHook> registeredCurrencies;
    private final CurrencyHook activeCurrency;

    public CurrencyRegistrationCompleteEvent(
            Map<String, CurrencyHook> registeredCurrencies,
            CurrencyHook activeCurrency
    ) {
        this.registeredCurrencies = registeredCurrencies;
        this.activeCurrency = activeCurrency;
    }

    @Override
    public @NotNull HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
