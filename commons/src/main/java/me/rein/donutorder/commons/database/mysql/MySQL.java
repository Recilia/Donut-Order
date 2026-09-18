package me.rein.donutorder.commons.database.mysql;

import me.rein.donutorder.commons.database.mysql.objects.EzqlDatabase;
import me.rein.donutorder.commons.database.mysql.settings.EzqlCredentials;

import java.sql.Connection;
import java.sql.SQLException;

public class MySQL {
    private EzqlDatabase database;

    public void init(
            String host,
            String databaseName,
            String username,
            String password,
            int port,
            int maxPool,
            boolean useSsl
    ) {
        try {
            database = new EzqlDatabase(new EzqlCredentials(
                    host,
                    databaseName,
                    username,
                    password,
                    maxPool,
                    port,
                    useSsl
            ));
            database.connect();
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize database: " + e.getMessage(), e);
        }
    }

    public void stop() {
        if (database != null) {
            try {
                database.disconnect();
            } catch (Exception ignored) {}
        }
    }

    public Connection getConnection() throws SQLException {
        if (database == null) {
            throw new SQLException("Database connection not initialized");
        }
        return database.getConnection();
    }
}
