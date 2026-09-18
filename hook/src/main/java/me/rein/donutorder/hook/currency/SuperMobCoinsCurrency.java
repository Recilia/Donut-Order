package me.rein.donutorder.hook.currency;

import me.rein.donutorder.api.currency.CurrencyHook;
import me.swanis.mobcoins.MobCoinsAPI;
import org.bukkit.OfflinePlayer;

public class SuperMobCoinsCurrency implements CurrencyHook {

    @Override
    public String getName() {
        return "SuperMobCoins";
    }

    @Override
    public void init() {}

    @Override
    public double getBalance(
            OfflinePlayer player
    ) {
        return MobCoinsAPI.getProfileManager().getProfile(player.getUniqueId()).getMobCoins();
    }

    @Override
    public void withdraw(
            OfflinePlayer player,
            double amount
    ) {
        MobCoinsAPI.getProfileManager().getProfile(player.getUniqueId()).setMobCoins(
                (long) (getBalance(player) - amount)
        );
    }

    @Override
    public void deposit(
            OfflinePlayer player,
            double amount
    ) {
        MobCoinsAPI.getProfileManager().getProfile(player.getUniqueId()).setMobCoins(
                (long) (getBalance(player) + amount)
        );
    }
}

