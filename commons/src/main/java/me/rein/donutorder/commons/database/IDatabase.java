package me.rein.donutorder.commons.database;

import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.objectholders.OrderLogEntry;

import java.util.List;
import java.util.UUID;

public interface IDatabase {
    // Utility operations
    void initialize();
    void close();

    // Order operations
    void createOrder(Order order);
    void deleteOrder(UUID orderId);
    void updateOrder(Order order);
    void updateClaimedAmount(UUID orderId, long newAmount);
    Order getOrder(UUID orderId);
    List<Order> getAllOrders();
    List<Order> getUnclaimedOrders(UUID playerId);
    List<Order> getPlayerOrders(UUID playerId);
    List<Order> getActiveOrders(); // Orders that haven't expired
    void updateDeliveredAmount(UUID orderId, long newAmount);
    void updateTotalPaid(UUID orderId, double newAmount);
    void deleteExpiredOrders();
    void handleExpiredOrder(Order order);

    // Order Logging
    void logOrderAction(String action, Order order, String details);
    List<OrderLogEntry> getPlayerHistory(UUID playerId, int limit);
    List<OrderLogEntry> getRecentHistory(int limit);
}
