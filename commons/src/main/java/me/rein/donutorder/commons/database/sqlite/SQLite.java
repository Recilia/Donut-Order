package me.rein.donutorder.commons.database.sqlite;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class SQLite {
    private Connection connection;
    private String path;

    public void init(String path){
        try {
            Class.forName("org.sqlite.JDBC");

            this.path = path;

            connection =  DriverManager.getConnection("jdbc:sqlite:"+path);
        } catch (SQLException | ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    public void stop(){
        try {
            connection.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    public Connection getConnection() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:"+path);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }
}

