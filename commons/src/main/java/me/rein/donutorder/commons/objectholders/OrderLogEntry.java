package me.rein.donutorder.commons.objectholders;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.UUID;

@Data
@AllArgsConstructor
public class OrderLogEntry {
    private String action;
    private UUID orderId;
    private UUID playerId;
    private String playerName;
    private String itemType;
    private long amount;
    private double price;
    private String details;
    private long timestamp;

    public String getFormattedTime() {
        return new SimpleDateFormat("MM/dd HH:mm").format(new Date(timestamp));
    }

    public String getFullFormattedTime() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date(timestamp));
    }
}