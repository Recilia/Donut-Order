package me.rein.donutorder.commands.args;

import lombok.Getter;
import me.rein.donutorder.commands.args.impl.ToggleNotifications;
import me.rein.donutorder.commands.args.impl.admin.Admin;

import java.util.Arrays;

public enum Args {
    ADMIN("admin", new Admin()),
//    ADMIN_ADD("add", new Add()),
//    ADMIN_RELOAD("reload", new Reload()),
//    BLACKLIST("blacklist", new Blacklist()),
    TOGGLENOTIFICATIONS("togglenotifications", new ToggleNotifications())
    ;

    @Getter
    private final String commandName;
    private final CommandArgs instance;

    Args(String commandName, CommandArgs instance) {
        this.commandName = commandName;
        this.instance = instance;
    }

    public CommandArgs get() {
        return instance;
    }

    public static Args fromString(String cmd) {
        return Arrays.stream(values())
                .filter(arg -> arg.getCommandName().equals(cmd.toLowerCase()))
                .findFirst()
                .orElse(null);
    }
}
