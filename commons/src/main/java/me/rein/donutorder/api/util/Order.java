package me.rein.donutorder.api.util;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.bukkit.inventory.ItemStack;

import java.util.UUID;

@Getter
@Setter
@ToString
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Order {
    @EqualsAndHashCode.Include
    private final UUID orderId;
    private final UUID creatorId;
    private final String playerName;
    private final ItemStack item;
    private final long totalAmount;
    private final double pricePerItem;
    private long deliveredAmount;
    private long claimedAmount;
    private double totalPaid;
    private final long creationTime;
    private final long duration;

    public Order(
            UUID orderId,
            UUID creatorId,
            String playerName,
            ItemStack item,
            long totalAmount,
            double pricePerItem,
            long deliveredAmount,
            double totalPaid,
            long duration
    ) {
        this.orderId = orderId;
        this.creatorId = creatorId;
        this.playerName = playerName;
        this.item = item;
        this.totalAmount = totalAmount;
        this.pricePerItem = pricePerItem;
        this.deliveredAmount = deliveredAmount;
        this.claimedAmount = 0;
        this.totalPaid = totalPaid;
        this.creationTime = System.currentTimeMillis();
        this.duration = duration;
    }

    private static long getDeletionThreshold() {
        return me.rein.donutorder.commons.manager.ValuesManager.getInstance().getDeletionThreshold();
    }

    public long getAvailableAmount() {
        return totalAmount - (deliveredAmount + claimedAmount);
    }

    public long getExpirationTime() {
        return creationTime + duration;
    }

    public long getRemainingTime() {
        return Math.max(0, getExpirationTime() - System.currentTimeMillis());
    }

    public long getDeletionRemainingTime() {
        return Math.max(0, getExpirationTime() + getDeletionThreshold() - System.currentTimeMillis());
    }

    public boolean isExpired() {
        return System.currentTimeMillis() > getExpirationTime() + getDeletionThreshold();
    }

    public boolean isInDeletionThreshold() {
        return System.currentTimeMillis() > getExpirationTime();
    }

    public double getTotalCost() {
        return totalAmount * pricePerItem;
    }

    public double getRemainingCost() {
        return getTotalCost() - totalPaid;
    }

    public boolean isComplete() {
        return deliveredAmount >= totalAmount;
    }
}
