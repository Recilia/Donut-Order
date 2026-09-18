package me.rein.donutorder.commons.scheduler.folialib;

import com.tcoded.folialib.wrapper.task.WrappedTask;
import me.rein.donutorder.commons.scheduler.task.Task;
import org.bukkit.plugin.Plugin;

public class FoliaLibTask implements Task {
    private final WrappedTask task;

    public FoliaLibTask(
            WrappedTask task
    ) {
        this.task = task;
    }

    @Override
    public Task getTask() {
        return this;
    }

    @Override
    public Plugin getOwner() {
        return task.getOwningPlugin();
    }

    @Override
    public boolean isCancelled() {
        return task.isCancelled();
    }

    @Override
    public void cancel() {
        task.cancel();
    }

    @Override
    public boolean isCurrentlyRunning() {
        return true;
    }

    @Override
    public boolean isRepeatingTask() {
        return false;
    }
}
