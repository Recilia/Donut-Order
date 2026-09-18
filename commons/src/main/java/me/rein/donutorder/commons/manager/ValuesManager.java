package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ValuesManager {

    @Getter(lazy = true)
    private static final ValuesManager instance = new ValuesManager();

    private String licenseKey;
    private boolean crossServerSupport;
    private boolean localization;
    private boolean fullLorePAPISupport;
    private boolean skipEnchants;
    private boolean stopAutoMigrationOnStart;
    private boolean announce;
    private boolean requireFullDurability;

    private boolean notificationsEnabled;
    private boolean notificationsToggle;

    private double pricingMin;
    private double pricingMax;
    private double pricingPerUnitMin;
    private double pricingPerUnitMax;

    private int durationPublic;
    private int durationExpiration;

    public long getDeletionThreshold() {
        return durationExpiration * 24L * 60L * 60L * 1000L;
    }

    private boolean foliaEnabled;
    private long clickCooldownMs;
    private boolean quickSearch;

    private String dayFormat = "d";
    private String daysFormat = "d";
    private String hourFormat = "h";
    private String hoursFormat = "h";
    private String minuteFormat = "m";
    private String minutesFormat = "m";
    private String secondFormat = "s";
    private String secondsFormat = "s";

    public void load() {
        FileConfiguration config = ConfigSelector.CONFIG.getConfig();

        licenseKey = config.getString("license-key");
        crossServerSupport = config.getBoolean("cross-server-support", false);
        localization = config.getBoolean("localization", false);
        fullLorePAPISupport = config.getBoolean("fullLorePAPISupport", false);
        skipEnchants = config.getBoolean("skipEnchants", false);
        stopAutoMigrationOnStart = config.getBoolean("stopAutoMigrationOnStart", false);
        announce = config.getBoolean("announce", true);
        requireFullDurability = config.getBoolean("require-full-durability", false);

        notificationsEnabled = config.getBoolean("notifications.enabled", true);
        notificationsToggle = config.getBoolean("notifications.toggle", true);

        // total price limits (new "pricing.total.min/max" with legacy "pricing.min/max" fallback)
        pricingMin = config.getDouble("pricing.total.min", config.getDouble("pricing.min", -1.0));
        pricingMax = config.getDouble("pricing.total.max", config.getDouble("pricing.max", -1.0));

        // per-unit price limits
        pricingPerUnitMin = config.getDouble("pricing.per-unit.min", -1.0);
        pricingPerUnitMax = config.getDouble("pricing.per-unit.max", -1.0);

        durationPublic = config.getInt("duration.public", 7);
        durationExpiration = config.getInt("duration.expiration", 7);

        clickCooldownMs = config.getLong("click-cooldown-ms", 100);
        quickSearch = config.getBoolean("quick-search", true);

        foliaEnabled = false;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            foliaEnabled = true;
        } catch (ClassNotFoundException e) {
            foliaEnabled = false;
        }

        ConfigurationSection timeSection = config.getConfigurationSection("timing");
        if (timeSection != null) {
            dayFormat = timeSection.getString("day", "d");
            daysFormat = timeSection.getString("days", "d");
            hourFormat = timeSection.getString("hour", "h");
            hoursFormat = timeSection.getString("hours", "h");
            minuteFormat = timeSection.getString("minute", "m");
            minutesFormat = timeSection.getString("minutes", "m");
            secondFormat = timeSection.getString("second", "s");
            secondsFormat = timeSection.getString("seconds", "s");
        }

    }
}
