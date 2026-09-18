package me.rein.donutorder.commons.scheduler.impl;

import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.scheduler.folialib.FoliaLibWrapper;
import me.rein.donutorder.commons.scheduler.task.Task;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

public class AsyncScheduler implements OScheduler {

    @Override
    public Task run(Runnable r) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runTaskAsynchronously();
    }

    @Override
    public Task runLater(Runnable r, long l) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runTaskLaterAsynchronously(l);
    }

    @Override
    public Task runRepeating(Runnable r, long l) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runTaskTimerAsynchronously(l, l);
    }

    @Override
    public Task runLocation(Runnable r, Location l) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runAtLocation(l);
    }

    @Override
    public Task runAtEntity(Runnable r, Entity e) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runAtEntity(e);
    }

    @Override
    public Task runAtEntityLater(Runnable r, Entity e, long delay) {
        return new FoliaLibWrapper() {
            @Override
            public void run() {
                r.run();
            }
        }.runAtEntityLater(e, delay);
    }
}
