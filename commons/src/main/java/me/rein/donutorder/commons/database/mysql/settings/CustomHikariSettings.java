package me.rein.donutorder.commons.database.mysql.settings;

import com.zaxxer.hikari.HikariConfig;

public interface CustomHikariSettings {

    HikariConfig getHikariConfig(EzqlCredentials ezqlCredentials);
}
