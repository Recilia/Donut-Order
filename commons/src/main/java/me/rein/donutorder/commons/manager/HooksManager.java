package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class HooksManager {

    @Getter(lazy = true)
    private static final HooksManager instance = new HooksManager();

    private String currentShop;
    private String currentCurrency;

    public void load() {
        FileConfiguration config = ConfigSelector.HOOKS.getConfig();

        currentShop = config.getString("shop");
        currentCurrency = config.getString("currency", "Vault");
    }
}

