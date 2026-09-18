package me.rein.donutorder.commons.enums.order;

import me.rein.donutorder.commons.enums.Messages;

public enum SortSelectItem {
    A_Z(Messages.SORT_SELECT_A_Z),
    Z_A(Messages.SORT_SELECT_Z_A),
    HIGHEST_PRICE(Messages.SORT_SELECT_HIGHEST_PRICE),
    LOWEST_PRICE(Messages.SORT_SELECT_LOWEST_PRICE);

    private final Messages message;

    SortSelectItem(Messages message) {
        this.message = message;
    }

    public String getDisplayName() {
        return message.getMessage();
    }

    public static SortSelectItem fromDisplayName(String displayName) {
        for (SortSelectItem type : values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        return A_Z;
    }
}
