package me.rein.donutorder.inventory;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

public interface InventoryHandler {
    void onClick(InventoryClickEvent event);

    default void onDrag(InventoryDragEvent event) {}

    void onOpen(InventoryOpenEvent event);

    void onClose(InventoryCloseEvent event);
}
