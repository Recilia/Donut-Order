package me.rein.donutorder.hook;

import lombok.Getter;
import lombok.Setter;
import org.bukkit.plugin.java.JavaPlugin;

@Getter
@Setter
public abstract class Hook {
    private boolean enabled = false;

    public abstract void init(JavaPlugin plugin);
}
