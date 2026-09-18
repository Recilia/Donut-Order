package me.rein.donutorder.api.event;

import lombok.Getter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public class PlayerQueryItemEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    @Getter private final Player player;
    @Getter private final String query;
    private final QueryItemList itemList;

    public PlayerQueryItemEvent(
            Player player,
            String query
    ) {
        super(true);

        this.player = player;
        this.query = query;
        this.itemList = new QueryItemList();
    }

    public QueryItemList getList() {
        return itemList;
    }

    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public static class QueryItemList {
        private final List<ItemStack> items = new ArrayList<>();

        public void addItem(ItemStack item) {
            items.add(item);
        }

        public List<ItemStack> getItems() {
            return items;
        }
    }
}
