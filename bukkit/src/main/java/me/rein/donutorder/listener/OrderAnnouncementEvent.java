package me.rein.donutorder.listener;

import me.rein.donutorder.api.event.cancellable.impl.PlayerOrderItemEvent;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.enums.Messages;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.commons.objectholders.TranslationPlaceholder;
import me.rein.donutorder.commons.util.message.NameUtil;
import me.rein.donutorder.commons.util.math.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class OrderAnnouncementEvent implements Listener {
    @EventHandler
    public void announce(PlayerOrderItemEvent event) {
        if (!ValuesManager.getInstance().isAnnounce())
            return;

        Player playerWhoOrdered = event.getPlayer();
        Order order = event.getOrder();

        for (Player player : Bukkit.getOnlinePlayers()) {
            /*if (player.getUniqueId().equals(playerWhoOrdered.getUniqueId()))
                continue;*/

            Messages.ORDER_ANNOUNCEMENT.send(player,
                    "{player}", playerWhoOrdered.getName(),
                    "{amount}", order.getTotalAmount() + "",
                    "{priceEach}", NumberUtil.formatPrice(order.getPricePerItem()),
                    "{itemType}", ValuesManager.getInstance().isLocalization() ? new TranslationPlaceholder("{itemType}",
                            order.getItem().getType().translationKey()) :
                            NameUtil.formatMaterialName("contact dev", order.getItem().getType().name())
                    );
        }
    }
}
