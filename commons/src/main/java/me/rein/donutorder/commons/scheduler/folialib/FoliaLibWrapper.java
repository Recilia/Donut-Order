package me.rein.donutorder.commons.scheduler.folialib;

import me.rein.donutorder.commons.Commons;
import me.rein.donutorder.commons.scheduler.task.Task;
import me.rein.donutorder.commons.scheduler.task.TaskScheduler;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

public class FoliaLibWrapper implements TaskScheduler, Runnable {

    @Override
    public void run() {

    }

    @Override
    public boolean isGlobalThread() {
        return Commons.getFoliaLib().getScheduler().isGlobalTickThread();
    }

    @Override
    public boolean isTickThread() {
        return Bukkit.getServer().isPrimaryThread();
    }

    @Override
    public boolean isEntityThread(Entity paramEntity) {
        return false;
    }

    @Override
    public boolean isRegionThread(Location paramLocation) {
        return false;
    }

    @Override
    public Task runTask() {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runLater(this, 1L));
    }

    @Override
    public Task runTaskLater(long paramLong) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runLater(this, paramLong));
    }

    @Override
    public Task runTaskTimer(long paramLong1, long paramLong2) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runTimer(this, paramLong1, paramLong2));
    }

    @Override
    public Task runAtLocation(Location paramLocation) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runAtLocationLater(paramLocation, this, 1L));
    }

    @Override
    public Task runAtEntity(Entity paramEntity) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runAtEntityLater(paramEntity, this, 1L));
    }

    @Override
    public Task runAtEntityLater(Entity paramEntity, long paramLong) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runAtEntityLater(paramEntity, this, paramLong));
    }

    @Override
    public Task runTaskAsynchronously() {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runLaterAsync(this, 1L));
    }

    @Override
    public Task runTaskLaterAsynchronously(long paramLong) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runLaterAsync(this, paramLong));
    }

    @Override
    public Task runTaskTimerAsynchronously(long paramLong1, long paramLong2) {
        return new FoliaLibTask(Commons.getFoliaLib().getScheduler().runTimerAsync(this, paramLong1, paramLong2));
    }

    @Override
    public void cancelTasks() {
        Commons.getFoliaLib().getScheduler().cancelAllTasks();
    }

    @Override
    public void cancelTasks(Plugin paramPlugin) {

    }
}