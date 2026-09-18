package me.rein.donutorder.commons.enums;

public enum ConfigType {
    ORDERS,
    NEW_ORDER,
    SELECT_ITEM,
    YOUR_ORDERS,
    EDIT_ORDER,
    EDIT_ORDER_CONFIRM,
    PICK_ENCHANTMENTS,
    COLLECT_ITEMS,
    ORDER_DELIVERY_CONFIRM
    ;

    public String getKey() {
        return name().toLowerCase();
    }
}
