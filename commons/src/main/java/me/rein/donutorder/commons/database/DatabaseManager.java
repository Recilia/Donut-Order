package me.rein.donutorder.commons.database;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.database.mysql.impl.MySQLDatabase;
import me.rein.donutorder.commons.database.sqlite.impl.SQLiteDatabase;
import me.rein.donutorder.commons.enums.ConfigSelector;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class DatabaseManager {

    @Getter(lazy = true)
    private static final DatabaseManager instance = new DatabaseManager();

    @Getter private static IDatabase database;

    public void load() {
        String type = ConfigSelector.DATABASES.getConfig().getString("type");

        switch (type.toLowerCase()) {
            case "donutdatabase" -> database = new SQLiteDatabase();

            case "mysql" -> database = new MySQLDatabase(
                    ConfigSelector.DATABASES.getConfig().getString("mysql.host"),
                    ConfigSelector.DATABASES.getConfig().getString("mysql.database"),
                    ConfigSelector.DATABASES.getConfig().getString("mysql.user"),
                    ConfigSelector.DATABASES.getConfig().getString("mysql.password"),
                    ConfigSelector.DATABASES.getConfig().getInt("mysql.port"),
                    ConfigSelector.DATABASES.getConfig().getInt("mysql.max-pool"),
                    ConfigSelector.DATABASES.getConfig().getBoolean("mysql.use-ssl")
            );

            default -> database = new SQLiteDatabase();
        }

        database.initialize();
    }
}
