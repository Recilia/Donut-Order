package me.rein.donutorder.commons.objectholders;

import lombok.Data;
import org.bukkit.configuration.ConfigurationSection;

@Data
public class OrderPermission {
    private final String permission;
    private final int orderLimit;
}
