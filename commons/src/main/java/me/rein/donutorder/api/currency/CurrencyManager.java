package me.rein.donutorder.api.currency;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.OfflinePlayer;

import java.util.HashMap;
import java.util.Map;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class CurrencyManager {

    @Getter(lazy = true)
    private static final CurrencyManager instance = new  CurrencyManager();

    private final Map<String, CurrencyHook> registeredCurrencies = new HashMap<>();
    private CurrencyHook activeCurrency = null;

    /**
     * Register a new currency integration
     * @param currencyHook The currency hook to register
     */
    public void registerCurrency(
            CurrencyHook currencyHook
    ) {
        registeredCurrencies.put(currencyHook.getName().toLowerCase(), currencyHook);
    }

    /**
     * Set active currency from config
     * @param currencyName Currency name from config
     * @return true if currency was set successfully
     */
    public boolean setActiveCurrency(
            String currencyName
    ) {
        if (currencyName.equalsIgnoreCase("none")) {
            activeCurrency = null;
            return true;
        }

        CurrencyHook currency = registeredCurrencies.get(currencyName.toLowerCase());

        if (currency == null)
            return false;

        activeCurrency = currency;
        currency.init();
        return true;
    }

    /**
     * Get player's balance
     * @param player Player to check
     * @return Balance or 0 if no currency active
     */
    public double getBalance(
            OfflinePlayer player
    ) {
        if (activeCurrency == null)
            return 0;

        return activeCurrency.getBalance(player);
    }

    /**
     * Remove amount from player's balance
     * @param player Player to withdraw from
     * @param amount Amount to withdraw
     */
    public void withdraw(
            OfflinePlayer player,
            double amount
    ) {
        if (activeCurrency == null)
            return;

        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount))
            return;

        activeCurrency.withdraw(player, amount);
    }

    /**
     * Add amount to player's balance
     * @param player Player to deposit to
     * @param amount Amount to deposit
     */
    public void deposit(
            OfflinePlayer player,
            double amount
    ) {
        if (activeCurrency == null)
            return;

        if (amount <= 0 || Double.isNaN(amount) || Double.isInfinite(amount))
            return;

        activeCurrency.deposit(player, amount);
    }
}

