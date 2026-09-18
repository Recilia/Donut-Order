package me.rein.donutorder.inventory;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public abstract class TakeOnlyInventoryGUI extends InventoryGUI {
    protected final List<Integer> takeableSlots;

    public TakeOnlyInventoryGUI(String title, List<Integer> takeableSlots) {
        super(title);
        this.takeableSlots = takeableSlots;
    }

    @Override
    public void onClick(InventoryClickEvent event) {
        event.setCancelled(true);

        // DOUBLE_CLICK / COLLECT_TO_CURSOR vacuum matching items from BOTH the
        // top and bottom inventories into the cursor. That bypasses our slot
        // logic entirely and could pull takeable items out without onItemTaken
        // ever firing (the actual dupe path on Folia/CanvasMC)
        ClickType click = event.getClick();
        InventoryAction action = event.getAction();
        if (click == ClickType.DOUBLE_CLICK || action == InventoryAction.COLLECT_TO_CURSOR) {
            return;
        }

        Inventory clicked = event.getClickedInventory();
        Inventory top = event.getView().getTopInventory();
        boolean isTopInventory = clicked == top;

        if (!isTopInventory) {
            if (!event.isShiftClick())
                event.setCancelled(false);

            return;
        }

        int slot = event.getSlot();
        ItemStack cursorItem = event.getCursor();

        if (cursorItem != null && !cursorItem.getType().isAir())
            return;

        InventoryButton button = buttonMap.get(slot);
        if (button != null) {
            button.getEventConsumer().accept(event);
            return;
        }

        boolean isTakeableSlot = takeableSlots.isEmpty() || takeableSlots.contains(slot);
        if (!isTakeableSlot) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType().isAir())
            return;

        if (click != ClickType.LEFT
                && click != ClickType.RIGHT
                && click != ClickType.SHIFT_LEFT
                && click != ClickType.SHIFT_RIGHT
                && click != ClickType.NUMBER_KEY
                && click != ClickType.DROP
                && click != ClickType.CONTROL_DROP
                && click != ClickType.SWAP_OFFHAND)
            return;

        event.setCancelled(false);
        onItemTaken((Player) event.getWhoClicked(), slot, clickedItem);
    }

    @Override
    public void onDrag(InventoryDragEvent event) {
        int inventorySize = event.getInventory().getSize();

        for (Integer slot : event.getRawSlots()) {
            if (slot < inventorySize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    protected abstract void onItemTaken(Player player, int slot, ItemStack item);
}
