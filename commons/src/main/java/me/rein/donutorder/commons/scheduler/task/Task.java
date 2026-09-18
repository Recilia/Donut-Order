package me.rein.donutorder.commons.scheduler.task;

import org.bukkit.plugin.Plugin;

public interface Task {
    Task getTask();

    Plugin getOwner();

    boolean isCancelled();

    void cancel();

    boolean isCurrentlyRunning();

    boolean isRepeatingTask();
}