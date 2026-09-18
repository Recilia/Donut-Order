package me.rein.donutorder.inventory;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class DraggableInventoryGUI extends InventoryGUI {
    @Getter protected final List<Integer> draggableSlots;
    private final Map<Integer, ItemStack> draggedItems = new HashMap<>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public DraggableInventoryGUI(String title, List<Integer> draggableSlots) {
        super(title);
        this.draggableSlots = draggableSlots;
    }

    public boolean markClosed() {
        return closed.compareAndSet(false, true);
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();

        if (event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (clicked == top) {
            if (draggableSlots.contains(event.getSlot())) {
                event.setCancelled(false);
                return;
            }
            super.onClick(event);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR) {
            event.setCancelled(true);
            return;
        }
        event.setCancelled(false);
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize && !draggableSlots.contains(rawSlot)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @Override
    public void onClose(InventoryCloseEvent event) {
        if (!markClosed())
            return;

        try {
            Map<Integer, ItemStack> itemsToHandle = new HashMap<>();

            for (int slot : draggableSlots) {
                ItemStack item = getInventory().getItem(slot);
                if (item != null) {
                    getInventory().setItem(slot, null);
                    itemsToHandle.put(slot, item.clone());
                }
            }

            handleDraggedItems((Player) event.getPlayer(), itemsToHandle);
        } finally {
            draggedItems.clear();
        }
    }

    /**
     * Handle the dragged items when the inventory is closed
     * @param player The player who closed the inventory
     * @param items Map of slot to ItemStack for all items in draggable slots
     */
    protected abstract void handleDraggedItems(Player player, Map<Integer, ItemStack> items);
}
