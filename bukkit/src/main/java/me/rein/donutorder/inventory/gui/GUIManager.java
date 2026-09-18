package me.rein.donutorder.inventory.gui;

import lombok.Getter;
import me.rein.donutorder.commons.util.helper.TimedHashSet;
import me.rein.donutorder.inventory.DraggableInventoryGUI;
import me.rein.donutorder.inventory.InventoryGUI;
import me.rein.donutorder.inventory.InventoryHandler;
import me.rein.donutorder.commons.manager.ValuesManager;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class GUIManager {

    private final Map<Inventory, InventoryHandler> activeInventories = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastClickTime = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentlyClosedPlayers = new ConcurrentHashMap<>();
    private static final long RECENTLY_CLOSED_GRACE_MS = 1500L;
    @Getter private static final TimedHashSet<Inventory> recentlyClosedInventories = new TimedHashSet<>();

    public void openGUI(
            InventoryGUI gui,
            Player player
    ) {
        this.registerHandledInventory(gui.getInventory(), gui);
        player.openInventory(gui.getInventory());
    }

    public void registerHandledInventory(
            Inventory inventory,
            InventoryHandler handler
    ) {
        this.activeInventories.put(inventory, handler);
    }

    public void unregisterInventory(
            Inventory inventory
    ) {
        this.activeInventories.remove(inventory);
    }

    public void handleClick(
            InventoryClickEvent event
    ) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());

        // Race condition fix: on Folia/CanvasMC, click and close events can
        // fire on different region threads. If the close already removed the
        // handler but the click is still in flight, we must still cancel it
        // to prevent vanilla from processing it (which would let the player
        // grab GUI items into their inventory).
        if (handler == null) {
            UUID playerId = event.getWhoClicked().getUniqueId();
            Long closedAt = recentlyClosedPlayers.get(playerId);
            if (closedAt != null && (System.currentTimeMillis() - closedAt) < RECENTLY_CLOSED_GRACE_MS) {
                event.setCancelled(true);
                return;
            }

            if (recentlyClosedInventories.containsAndRefresh(event.getInventory()))
                event.setCancelled(true);

            return;
        }

        if (!(handler instanceof DraggableInventoryGUI)) {
            event.setCancelled(true);

            UUID playerId = event.getWhoClicked().getUniqueId();
            long now = System.currentTimeMillis();
            Long lastClick = this.lastClickTime.get(playerId);

            if (lastClick != null && (now - lastClick) < ValuesManager.getInstance().getClickCooldownMs())
                return;

            this.lastClickTime.put(playerId, now);
        }

        handler.onClick(event);
    }

    public void handleDrag(
            InventoryDragEvent event
    ) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());

        if (handler == null) {
            UUID playerId = event.getWhoClicked().getUniqueId();
            Long closedAt = recentlyClosedPlayers.get(playerId);
            if (closedAt != null && (System.currentTimeMillis() - closedAt) < RECENTLY_CLOSED_GRACE_MS) {
                event.setCancelled(true);
            }
            return;
        }

        handler.onDrag(event);
    }

    public void handleOpen(
            InventoryOpenEvent event
    ) {
        InventoryHandler handler = this.activeInventories.get(event.getInventory());

        if (handler == null)
            return;

        handler.onOpen(event);
    }

    public void handleClose(
            InventoryCloseEvent event
    ) {
        Inventory inventory = event.getInventory();
        InventoryHandler handler = this.activeInventories.get(inventory);

        if (handler == null)
            return;

        UUID playerId = event.getPlayer().getUniqueId();

        recentlyClosedPlayers.put(playerId, System.currentTimeMillis());
        recentlyClosedInventories.add(inventory);

        try {
            handler.onClose(event);
        } finally {
            this.unregisterInventory(inventory);
            this.lastClickTime.remove(playerId);
        }
    }
}
