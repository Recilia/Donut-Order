package me.rein.donutorder.commons;

import com.tcoded.folialib.FoliaLib;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.plugin.java.JavaPlugin;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class Commons {

    @Getter private static JavaPlugin plugin;
    @Getter private static FoliaLib foliaLib;

    public static void load(
            JavaPlugin plugin
    ) {
        Commons.plugin = plugin;
        Commons.foliaLib = new FoliaLib(plugin);
    }
}
