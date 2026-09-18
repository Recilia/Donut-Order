package me.rein.donutorder.commons.enums.order;

import me.rein.donutorder.commons.enums.Messages;

public enum SortOrders {
    MOST_PAID(Messages.SORT_MOST_PAID),
    MOST_DELIVERED(Messages.SORT_MOST_DELIVERED),
    RECENTLY_LISTED(Messages.SORT_RECENTLY_LISTED),
    MOST_MONEY_PER_ITEM(Messages.SORT_MOST_MONEY_PER_ITEM);

    private final Messages message;

    SortOrders(Messages message) {
        this.message = message;
    }

    public String getDisplayName() {
        return message.getMessage();
    }

    public static SortOrders fromDisplayName(String displayName) {
        for (SortOrders type : values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        return MOST_PAID;
    }
}
