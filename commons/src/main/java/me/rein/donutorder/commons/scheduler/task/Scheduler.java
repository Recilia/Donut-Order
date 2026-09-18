package me.rein.donutorder.commons.scheduler.task;

public interface Scheduler {
    void async(Runnable runnable);
    void sync(Runnable runnable);
}
