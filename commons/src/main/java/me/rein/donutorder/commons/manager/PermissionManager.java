package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.objectholders.OrderPermission;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PermissionManager {

    @Getter(lazy = true)
    private static final PermissionManager instance = new PermissionManager();

    private final Map<String, OrderPermission> orderPermission = new ConcurrentHashMap<>();
    private List<String> orderLimits;
    private String orderAdmin;

    public void load() {
        orderLimits = ConfigSelector.CONFIG.getConfig().isSet("permissions.order-limits") ?
                ConfigSelector.CONFIG.getConfig().getStringList("permissions.order-limits") :
                Collections.emptyList();
        orderAdmin = ConfigSelector.CONFIG.getConfig().getString("permissions.order-admin", "order.admin");

        loadOrderPermissions();
    }

    public int getMaxOrdersForPlayer(Player player) {
        int maxForPlayer = player.hasPermission(this.orderAdmin) ? 26 : -1;

        if (maxForPlayer == -1) {
            for (PermissionAttachmentInfo effectivePermission : player.getEffectivePermissions()) {
                if (!effectivePermission.getValue()) continue;

                String perm = effectivePermission.getPermission();

                if (!getOrderPermission().containsKey(perm)) continue;

                OrderPermission permission = getOrderPermission().get(perm);

                if (maxForPlayer > permission.getOrderLimit())
                    continue;

                maxForPlayer = permission.getOrderLimit();
            }
        }

        return maxForPlayer == -1 ? 26 : maxForPlayer;
    }

    private void loadOrderPermissions() {
        for (String orderLimit : this.orderLimits) {
            try {
                String[] strings = orderLimit.split(":");

                String perm = strings[0];
                int limit = Integer.parseInt(strings[1]);

                orderPermission.put(perm, new OrderPermission(perm, limit));
            } catch (Exception e) {
                Bukkit.getLogger().severe("DonutOrder | Error when parsing permissions! Skipping...");
            }
        }
    }
}
