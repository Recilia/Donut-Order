package me.rein.donutorder.api.currency;

import org.bukkit.OfflinePlayer;

public interface CurrencyHook {

    String getName();
    void init();

    double getBalance(OfflinePlayer player);
    void withdraw(OfflinePlayer player, double amount);
    void deposit(OfflinePlayer player, double amount);
}
