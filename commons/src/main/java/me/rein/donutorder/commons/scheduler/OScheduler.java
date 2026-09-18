package me.rein.donutorder.commons.scheduler;

import me.rein.donutorder.commons.scheduler.impl.AsyncScheduler;
import me.rein.donutorder.commons.scheduler.impl.SyncScheduler;
import me.rein.donutorder.commons.scheduler.task.Task;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public interface OScheduler {
    String CRAFT_SCHEDULER_ERROR = "Error with Craft Scheduler: ";

    static OScheduler async() {
        return new AsyncScheduler();
    }

    static OScheduler sync() {
        return new SyncScheduler();
    }

    Task run(Runnable r);

    Task runLater(Runnable r, long l);

    Task runRepeating(Runnable r, long l);

    Task runLocation(Runnable r, Location l);

    Task runAtEntity(Runnable r, Entity e);

    Task runAtEntityLater(Runnable r, Entity e, long delay);

    default void logError(String message) {
      //  Commons.getLogger().error(CRAFT_SCHEDULER_ERROR + message);
        Bukkit.getLogger().warning(CRAFT_SCHEDULER_ERROR + message);
    }
}
