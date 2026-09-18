package me.rein.donutorder.inventory;

import lombok.Getter;
import me.rein.donutorder.commons.enums.ConfigType;
import me.rein.donutorder.commons.manager.ItemManager;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.message.ChatUtil;
import me.rein.donutorder.commons.util.item.ItemBuilder;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public abstract class InventoryGUI implements InventoryHandler {

    @Getter
    protected final Inventory inventory;
    protected final Map<Integer, InventoryButton> buttonMap = new HashMap<>();
    protected final String title;

    public InventoryGUI(
            String title
    ) {
        this.title = ChatUtil.c(title);
        this.inventory = this.createInventory();
    }

    public void addButton(
            int slot,
            InventoryButton button
    ) {
        if (slot == -1)
            return;

        this.buttonMap.put(slot, button);
    }

    public void decorate(
            Player player
    ) {
        this.buttonMap.forEach((slot, button) -> {
            if (button.isAsync()) {
                CompletableFuture<ItemStack> future = CompletableFuture.supplyAsync(
                        () -> button.getIconCreator().apply(player));

                future.thenAccept(item -> {
                    if (item == null)
                        return;

                    OScheduler.sync().runAtEntity(() -> this.inventory.setItem(slot, item), player);
                        /*Commons.getFoliaLib().getScheduler().runAtEntity(player, task -> {
                            this.inventory.setItem(slot, item);
                        });*/
                });

                return;
            }

            ItemBuilder icon = button.getIconCreator().apply(player);
            this.inventory.setItem(slot, icon);
        });
    }

    protected void fillDecorationsFromConfig(
            ConfigType configType
    ) {
        ItemManager.ConfigItems items = ItemManager.getInstance().getConfigItemsMap().get(configType.getKey());
        if (items == null)
            return;

        items.getNormalItems().forEach((key, item) -> {
            for (int slot : item.getSlots()) {
                if (slot > -1 && slot < this.inventory.getSize() && !this.buttonMap.containsKey(slot))
                    this.inventory.setItem(slot, item.getItem());
            }
        });
    }

    @Override
    public void onClick(
            InventoryClickEvent event
    ) {
        event.setCancelled(true);

        if (event.getClickedInventory() != event.getView().getTopInventory())
            return;

        int slot = event.getSlot();
        InventoryButton button = this.buttonMap.get(slot);

        if (button == null)
            return;

        button.getEventConsumer().accept(event);
    }

    @Override
    public void onOpen(
            InventoryOpenEvent event
    ) {
        this.decorate((Player) event.getPlayer());
    }

    @Override
    public void onClose(InventoryCloseEvent event) {}

    protected abstract Inventory createInventory();
}
