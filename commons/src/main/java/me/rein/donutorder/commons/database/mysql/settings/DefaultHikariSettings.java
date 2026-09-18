package me.rein.donutorder.commons.database.mysql.settings;

import com.zaxxer.hikari.HikariConfig;

public class DefaultHikariSettings implements CustomHikariSettings{
    @Override
    public HikariConfig getHikariConfig(EzqlCredentials creds) {
        HikariConfig hikariConfig = new HikariConfig();

        hikariConfig.setJdbcUrl("jdbc:mysql://" + creds.getHost() + ":"
                + creds.getPort() + "/" + creds.getDatabase() + "?allowPublicKeyRetrieval=true&useSSL=" + creds.useSSL());
        hikariConfig.setDriverClassName("com.mysql.cj.jdbc.Driver");
        hikariConfig.setUsername(creds.getUsername());
        hikariConfig.setPassword(creds.getPassword());
        hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
        hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
        hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setAutoCommit(true);
        hikariConfig.setMaximumPoolSize(creds.getMaxPool());

        hikariConfig.setMaxLifetime(180000);
        hikariConfig.setIdleTimeout(60000);
        hikariConfig.setKeepaliveTime(30000);
        hikariConfig.setConnectionTimeout(30000);

        return hikariConfig;
    }
}
