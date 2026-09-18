package me.rein.donutorder.hook.currency;

import me.rein.donutorder.api.currency.CurrencyHook;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

public class VaultCurrency implements CurrencyHook {

    private Economy economy = null;

    @Override
    public String getName() {
        return "Vault";
    }

    @Override
    public void init() {
        RegisteredServiceProvider<Economy> economyProvider =
                Bukkit.getServer().getServicesManager().getRegistration(Economy.class);

        if (economyProvider != null)
            economy = economyProvider.getProvider();
    }

    @Override
    public double getBalance(
            OfflinePlayer player
    ) {
        if (economy == null)
            init();

        return economy.getBalance(player);
    }

    @Override
    public void withdraw(OfflinePlayer player, double amount) {
        if (economy == null)
            init();

        economy.withdrawPlayer(player, amount);
    }

    @Override
    public void deposit(
            OfflinePlayer player,
            double amount
    ) {
        if (economy == null)
            init();

        economy.depositPlayer(player, amount);
    }
}

