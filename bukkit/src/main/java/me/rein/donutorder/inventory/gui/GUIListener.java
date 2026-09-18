package me.rein.donutorder.inventory.gui;

import lombok.AllArgsConstructor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;

@AllArgsConstructor
public class GUIListener implements Listener {

    private final GUIManager guiManager;

    @EventHandler(priority = EventPriority.LOWEST)
    public void onClick(
            InventoryClickEvent event
    ) {
        this.guiManager.handleClick(event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onDrag(
            InventoryDragEvent event
    ) {
        this.guiManager.handleDrag(event);
    }

    @EventHandler
    public void onOpen(
            InventoryOpenEvent event
    ) {
        this.guiManager.handleOpen(event);
    }

    @EventHandler
    public void onClose(
            InventoryCloseEvent event
    ) {
        this.guiManager.handleClose(event);
    }
}
