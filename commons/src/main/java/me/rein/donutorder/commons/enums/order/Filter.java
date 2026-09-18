package me.rein.donutorder.commons.enums.order;

import lombok.Getter;
import me.rein.donutorder.commons.enums.Messages;

@Getter
public enum Filter {
    ALL(Messages.FILTER_ALL),
    BLOCKS(Messages.FILTER_BLOCKS),
    TOOLS(Messages.FILTER_TOOLS),
    FOOD(Messages.FILTER_FOOD),
    COMBAT(Messages.FILTER_COMBAT),
    POTIONS(Messages.FILTER_POTIONS),
    BOOKS(Messages.FILTER_BOOKS),
    INGREDIENTS(Messages.FILTER_INGREDIENTS),
    UTILITIES(Messages.FILTER_UTILITIES)
    ;

    private final Messages message;

    Filter(Messages message) {
        this.message = message;
    }

    public String getDisplayName() {
        return message.getMessage();
    }

    public static Filter fromDisplayName(String displayName) {
        for (Filter filter : values()) {
            if (filter.getDisplayName().equals(displayName)) {
                return filter;
            }
        }
        return ALL;
    }

    public static boolean isValidDisplayName(String name) {
        for (Filter filter : values()) {
            if (filter.getDisplayName().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
