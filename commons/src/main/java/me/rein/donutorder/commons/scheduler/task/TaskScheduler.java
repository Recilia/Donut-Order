package me.rein.donutorder.commons.scheduler.task;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public interface TaskScheduler {
    boolean isGlobalThread();

    boolean isTickThread();

    boolean isEntityThread(Entity paramEntity);

    boolean isRegionThread(Location paramLocation);

    Task runTask();

    Task runTaskLater(long paramLong);

    Task runTaskTimer(long paramLong1, long paramLong2);

    Task runAtLocation(Location paramLocation);

    Task runAtEntity(Entity paramEntity);

    Task runAtEntityLater(Entity paramEntity, long paramLong);

    @Deprecated
    default Task runTask(Plugin plugin) {
        return runTask();
    }

    @Deprecated
    default Task runTaskLater(Plugin plugin, long delay) {
        return runTaskLater(delay);
    }

    @Deprecated
    default Task runTaskTimer(Plugin plugin, long delay, long period) {
        return runTaskTimer(delay, period);
    }

    default Task runTask(Location location) {
        return runTask();
    }

    default Task runTaskLater(Location location, long delay) {
        return runTaskLater(delay);
    }

    @Deprecated
    default Task scheduleSyncDelayedTask(long delay) {
        return runTaskLater(delay);
    }

    @Deprecated
    default Task scheduleSyncDelayedTask() {
        return runTask();
    }

    default Task runTaskTimer(Location location, long delay, long period) {
        return runTaskTimer(delay, period);
    }

    @Deprecated
    default Task scheduleSyncRepeatingTask(long delay, long period) {
        return runTaskTimer(delay, period);
    }

    default Task runTask(Entity entity) {
        return runTask();
    }

    default Task runTaskLater(Entity entity, long delay) {
        return runTaskLater(delay);
    }

    default Task runTaskTimer(Entity entity, long delay, long period) {
        return runTaskTimer(delay, period);
    }

    Task runTaskAsynchronously();

    Task runTaskLaterAsynchronously(long paramLong);

    Task runTaskTimerAsynchronously(long paramLong1, long paramLong2);

    @Deprecated
    default Task runTaskAsynchronously(Plugin plugin) {
        return runTaskAsynchronously();
    }

    @Deprecated
    default Task runTaskLaterAsynchronously(Plugin plugin, long delay) {
        return runTaskLaterAsynchronously(delay);
    }

    default Task runAtLocation(Plugin plugin, Location location) {
        return runAtLocation(location);
    }

    @Deprecated
    default Task runTaskTimerAsynchronously(Plugin plugin, long delay, long period) {
        return runTaskTimerAsynchronously(delay, period);
    }

    void cancelTasks();

    void cancelTasks(Plugin paramPlugin);
}
