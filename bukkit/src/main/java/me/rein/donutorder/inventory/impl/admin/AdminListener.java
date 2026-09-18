package me.rein.donutorder.inventory.impl.admin;

import lombok.Getter;
import me.rein.donutorder.commons.manager.MaterialManager;
import me.rein.donutorder.commons.manager.price.PriceManager;
import me.rein.donutorder.commons.util.message.ChatUtil;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AdminListener implements Listener {

    private static final @Getter Set<UUID> addItem = new HashSet<>();

    @EventHandler
    public void onChatAddItem(
            AsyncPlayerChatEvent event
    ) {
        Player player = event.getPlayer();

        if (!addItem.contains(player.getUniqueId()))
            return;

        event.setCancelled(true);

        String message = ChatColor.stripColor(event.getMessage());

        double price = -1;
        try {
            price = Double.parseDouble(message);
            if (Double.isNaN(price) || Double.isInfinite(price) || price <= 0) {
                player.sendMessage(ChatUtil.c("&cInvalid price!"));
                return;
            }
        } catch (Exception ignored) {
            player.sendMessage(ChatUtil.c("&cInvalid price!"));
            return;
        }

        ItemStack itemStack = player.getInventory().getItemInHand();

        if (itemStack.getType() == Material.AIR) {
            player.sendMessage(ChatUtil.c("&cInvalid item!"));
            return;
        }

        ItemStack clonedItemStack = itemStack.clone();
        clonedItemStack.setAmount(1);

        PriceManager.getInstance().addOrUpdatePrice(clonedItemStack, price);
        MaterialManager.getInstance().getMaterials()
                .put(clonedItemStack, new MaterialManager.MaterialData(
                        clonedItemStack,
                        MaterialManager.getInstance().getCategory(clonedItemStack),
                        (int) price
                ));

        player.sendMessage(ChatUtil.c("&aItem added!"));

        addItem.remove(player.getUniqueId());
    }
}
