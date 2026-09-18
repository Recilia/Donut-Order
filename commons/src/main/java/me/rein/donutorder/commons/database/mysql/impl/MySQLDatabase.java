package me.rein.donutorder.commons.database.mysql.impl;

import me.rein.donutorder.api.event.OrderExpirationEvent;
import me.rein.donutorder.commons.database.IDatabase;
import me.rein.donutorder.commons.database.mysql.MySQL;
import me.rein.donutorder.commons.manager.ValuesManager;
import me.rein.donutorder.api.util.Order;
import me.rein.donutorder.commons.objectholders.OrderLogEntry;
import me.rein.donutorder.commons.scheduler.OScheduler;
import me.rein.donutorder.commons.util.compression.ZStdUtil;
import org.bukkit.Bukkit;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder;

import java.io.ByteArrayInputStream;
import java.sql.*;
import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

public class MySQLDatabase implements IDatabase {
    private final MySQL mysql;

    private final Map<UUID, String> playerNameCache = new ConcurrentHashMap<>();
    private final Queue<PlayerNameUpdate> playerNameUpdateQueue = new LinkedList<>();
    private final BlockingQueue<LogEntry> logQueue = new LinkedBlockingQueue<>();

    private class PlayerNameUpdate {
        final UUID orderId;
        final UUID playerId;
        final String playerName;

        PlayerNameUpdate(UUID orderId, UUID playerId, String playerName) {
            this.orderId = orderId;
            this.playerId = playerId;
            this.playerName = playerName;
        }
    }

    private static class LogEntry {
        final String action;
        final UUID orderId;
        final UUID playerId;
        final String playerName;
        final String itemType;
        final long amount;
        final double price;
        final String details;
        final long timestamp;

        LogEntry(String action, Order order, String details) {
            this.action = action;
            this.orderId = order.getOrderId();
            this.playerId = order.getCreatorId();
            this.playerName = order.getPlayerName();
            this.itemType = order.getItem() != null ? order.getItem().getType().name() : "UNKNOWN";
            this.amount = order.getTotalAmount();
            this.price = order.getPricePerItem();
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }

        LogEntry(String action, UUID orderId, UUID playerId, String playerName, String details) {
            this.action = action;
            this.orderId = orderId;
            this.playerId = playerId;
            this.playerName = playerName;
            this.itemType = null;
            this.amount = 0;
            this.price = 0.0;
            this.details = details;
            this.timestamp = System.currentTimeMillis();
        }
    }

    public MySQLDatabase(
            String host,
            String database,
            String username,
            String password,
            int port,
            int maxPool,
            boolean useSsl
    ) {
        this.mysql = new MySQL();
        mysql.init(host, database, username, password, port, maxPool, useSsl);

        OScheduler.async().runRepeating(this::processPlayerNameUpdateQueue, 200L);
        OScheduler.async().runRepeating(this::processLogQueue, 100L);
    }

    @Override
    public void initialize() {
        try (Connection conn = mysql.getConnection();
             Statement stmt = conn.createStatement()) {

            boolean tableExists;
            boolean needsMigration = false;

            try (PreparedStatement checkTableStmt = conn.prepareStatement(
                    "SELECT TABLE_NAME FROM INFORMATION_SCHEMA.TABLES " +
                            "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'orders'")) {
                try (ResultSet rs = checkTableStmt.executeQuery()) {
                    tableExists = rs.next();
                }
            }

            if (tableExists) {
                try (PreparedStatement checkColumnStmt = conn.prepareStatement(
                        "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                                "WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'item_data' AND TABLE_SCHEMA = DATABASE()")) {
                    try (ResultSet rs = checkColumnStmt.executeQuery()) {
                        if (rs.next() && "text".equalsIgnoreCase(rs.getString("DATA_TYPE"))) {
                            needsMigration = true;
                        }
                    }
                }

                if (needsMigration) {
                    migrateTextToBlobData();
                }

                addPlayerNameColumnIfNeeded();
                loadPlayerNameCache();
            } else {
                stmt.execute("CREATE TABLE IF NOT EXISTS orders (" +
                        "    order_id VARCHAR(36) PRIMARY KEY," +
                        "    creator_id VARCHAR(36) NOT NULL," +
                        "    player_name VARCHAR(36)," +
                        "    item_data LONGBLOB NOT NULL," +
                        "    total_amount BIGINT NOT NULL," +
                        "    price_per_item DOUBLE NOT NULL," +
                        "    delivered_amount BIGINT NOT NULL," +
                        "    claimed_amount BIGINT NOT NULL," +
                        "    total_paid DOUBLE NOT NULL," +
                        "    creation_time BIGINT NOT NULL," +
                        "    duration BIGINT NOT NULL" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
            }

            stmt.execute("CREATE TABLE IF NOT EXISTS order_logs (" +
                    "    log_id INT AUTO_INCREMENT PRIMARY KEY," +
                    "    timestamp BIGINT NOT NULL," +
                    "    action VARCHAR(50) NOT NULL," +
                    "    order_id VARCHAR(36) NOT NULL," +
                    "    player_id VARCHAR(36) NOT NULL," +
                    "    player_name VARCHAR(36)," +
                    "    item_type VARCHAR(100)," +
                    "    amount BIGINT," +
                    "    price DOUBLE," +
                    "    details TEXT," +
                    "    INDEX idx_player_id (player_id)," +
                    "    INDEX idx_timestamp (timestamp DESC)," +
                    "    INDEX idx_order_id (order_id)" +
                    ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");

            migrateIntToBigint(conn);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    private void processLogQueue() {
        if (logQueue.isEmpty()) {
            return;
        }

        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO order_logs (timestamp, action, order_id, player_id, player_name, " +
                             "item_type, amount, price, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

            int count = 0;
            while (!logQueue.isEmpty() && count < 100) {
                LogEntry entry = logQueue.poll();
                if (entry != null) {
                    ps.setLong(1, entry.timestamp);
                    ps.setString(2, entry.action);
                    ps.setString(3, entry.orderId.toString());
                    ps.setString(4, entry.playerId.toString());
                    ps.setString(5, entry.playerName);
                    ps.setString(6, entry.itemType);
                    ps.setLong(7, entry.amount);
                    ps.setDouble(8, entry.price);
                    ps.setString(9, entry.details);
                    ps.addBatch();
                    count++;
                }
            }

            if (count > 0) {
                ps.executeBatch();
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Failed to write logs to database: " + e.getMessage());
        }
    }

    @Override
    public void logOrderAction(String action, Order order, String details) {
        logQueue.offer(new LogEntry(action, order, details));
    }

    @Override
    public List<OrderLogEntry> getPlayerHistory(UUID playerId, int limit) {
        List<OrderLogEntry> logs = new ArrayList<>();
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM order_logs WHERE player_id = ? " +
                             "ORDER BY timestamp DESC LIMIT ?")) {

            ps.setString(1, playerId.toString());
            ps.setInt(2, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new OrderLogEntry(
                            rs.getString("action"),
                            UUID.fromString(rs.getString("order_id")),
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("player_name"),
                            rs.getString("item_type"),
                            rs.getLong("amount"),
                            rs.getDouble("price"),
                            rs.getString("details"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    @Override
    public List<OrderLogEntry> getRecentHistory(int limit) {
        List<OrderLogEntry> logs = new ArrayList<>();
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM order_logs ORDER BY timestamp DESC LIMIT ?")) {

            ps.setInt(1, limit);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    logs.add(new OrderLogEntry(
                            rs.getString("action"),
                            UUID.fromString(rs.getString("order_id")),
                            UUID.fromString(rs.getString("player_id")),
                            rs.getString("player_name"),
                            rs.getString("item_type"),
                            rs.getLong("amount"),
                            rs.getDouble("price"),
                            rs.getString("details"),
                            rs.getLong("timestamp")
                    ));
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return logs;
    }

    private void loadPlayerNameCache() {
        try (Connection conn = mysql.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT creator_id, player_name FROM orders WHERE player_name IS NOT NULL")) {

            while (rs.next()) {
                String creatorId = rs.getString("creator_id");
                String playerName = rs.getString("player_name");
                if (creatorId != null && playerName != null) {
                    playerNameCache.put(UUID.fromString(creatorId), playerName);
                }
            }

            Bukkit.getLogger().info("Loaded " + playerNameCache.size() + " player names into cache");
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Failed to load player name cache: " + e.getMessage());
        }
    }

    private void processPlayerNameUpdateQueue() {
        if (playerNameUpdateQueue.isEmpty()) {
            return;
        }

        Bukkit.getLogger().info("Processing " + playerNameUpdateQueue.size() + " player name updates");

        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET player_name = ? WHERE order_id = ?")) {

            int batchCount = 0;
            synchronized (playerNameUpdateQueue) {
                while (!playerNameUpdateQueue.isEmpty() && batchCount < 50) {
                    PlayerNameUpdate update = playerNameUpdateQueue.poll();
                    ps.setString(1, update.playerName);
                    ps.setString(2, update.orderId.toString());
                    ps.addBatch();
                    batchCount++;
                }
            }

            if (batchCount > 0) {
                int[] results = ps.executeBatch();
                Bukkit.getLogger().info("Updated " + Arrays.stream(results).sum() + " player names in database");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("Error updating player names: " + e.getMessage());
        }
    }

    private String getPlayerName(UUID playerId) {
        String cachedName = playerNameCache.get(playerId);
        if (cachedName != null) {
            return cachedName;
        }

        String playerName = Bukkit.getOfflinePlayer(playerId).getName();
        if (playerName != null) {
            playerNameCache.put(playerId, playerName);
        }
        return playerName;
    }

    private void queuePlayerNameUpdate(UUID orderId, UUID playerId, String playerName) {
        synchronized (playerNameUpdateQueue) {
            playerNameUpdateQueue.add(new PlayerNameUpdate(orderId, playerId, playerName));
        }
    }

    private void addPlayerNameColumnIfNeeded() {
        try (Connection conn = mysql.getConnection()) {
            boolean columnExists = false;
            try (PreparedStatement checkColumnStmt = conn.prepareStatement(
                    "SELECT COLUMN_NAME FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'player_name' AND TABLE_SCHEMA = DATABASE()")) {
                try (ResultSet rs = checkColumnStmt.executeQuery()) {
                    columnExists = rs.next();
                }
            }

            if (!columnExists) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE orders ADD COLUMN player_name VARCHAR(36)");
                    Bukkit.getLogger().info("Added player_name column to orders table in MySQL database");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("Failed to add player_name column to orders table in MySQL database");
        }
    }

    @Override
    public void close() {
        processLogQueue();
        processPlayerNameUpdateQueue();
        mysql.stop();
    }

    @Override
    public void createOrder(Order order) {
        final Order finalOrder;
        if (order.getPlayerName() == null) {
            String playerName = getPlayerName(order.getCreatorId());
            finalOrder = new Order(
                    order.getOrderId(),
                    order.getCreatorId(),
                    playerName,
                    order.getItem(),
                    order.getTotalAmount(),
                    order.getPricePerItem(),
                    order.getDeliveredAmount(),
                    order.getClaimedAmount(),
                    order.getTotalPaid(),
                    order.getCreationTime(),
                    order.getDuration()
            );
        } else {
            finalOrder = order;
            playerNameCache.put(order.getCreatorId(), order.getPlayerName());
        }

        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO orders (order_id, creator_id, player_name, item_data, total_amount, price_per_item, " +
                             "delivered_amount, claimed_amount, total_paid, creation_time, duration) " +
                             "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setString(1, finalOrder.getOrderId().toString());
            ps.setString(2, finalOrder.getCreatorId().toString());
            ps.setString(3, finalOrder.getPlayerName());
            ps.setBytes(4, ZStdUtil.serializeItemStack(finalOrder.getItem()));
            ps.setLong(5, finalOrder.getTotalAmount());
            ps.setDouble(6, finalOrder.getPricePerItem());
            ps.setLong(7, finalOrder.getDeliveredAmount());
            ps.setLong(8, finalOrder.getClaimedAmount());
            ps.setDouble(9, finalOrder.getTotalPaid());
            ps.setLong(10, finalOrder.getCreationTime());
            ps.setLong(11, finalOrder.getDuration());
            ps.executeUpdate();

            logOrderAction("CREATE", finalOrder, String.format("Created order for %dx %s at $%.2f each",
                    finalOrder.getTotalAmount(), finalOrder.getItem().getType().name(), finalOrder.getPricePerItem()));

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteOrder(UUID orderId) {
        Order order = getOrder(orderId);

        /*try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE order_id = ?")) {
            ps.setString(1, orderId.toString());
            ps.executeUpdate();

            if (order != null) {
                logOrderAction("DELETE", order, "Order deleted manually");
            } else {
                logQueue.offer(new LogEntry("DELETE", orderId, UUID.randomUUID(), "Unknown", "Order deleted (details unavailable)"));
            }

        } catch (SQLException e) {
            e.printStackTrace();
        }*/

        deleteOrderInternal(orderId);

        if (order == null) {
            logQueue.offer(new LogEntry(
                    "DELETE",
                    orderId,
                    UUID.randomUUID(),
                    "Unknown",
                    "Order deleted (details unavailable)")
            );
        }

        logOrderAction("DELETE", order, "Order deleted manually");
    }

    private void deleteOrderInternal(UUID orderId) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement("DELETE FROM orders WHERE order_id = ?")) {
            ps.setString(1, orderId.toString());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateOrder(Order order) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET delivered_amount = ?, claimed_amount = ?, total_paid = ?, player_name = ? " +
                             "WHERE order_id = ? AND (creation_time + duration + ?) > ?")) {
            ps.setLong(1, order.getDeliveredAmount());
            ps.setLong(2, order.getClaimedAmount());
            ps.setDouble(3, order.getTotalPaid());

            String playerName = order.getPlayerName();
            if (playerName == null) {
                playerName = getPlayerName(order.getCreatorId());
            } else {
                playerNameCache.put(order.getCreatorId(), playerName);
            }
            ps.setString(4, playerName);

            ps.setString(5, order.getOrderId().toString());
            ps.setLong(6, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(7, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateClaimedAmount(UUID orderId, long newAmount) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET claimed_amount = ? WHERE order_id = ? " +
                             "AND (creation_time + duration + ?) > ?")) {
            ps.setLong(1, newAmount);
            ps.setString(2, orderId.toString());
            ps.setLong(3, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public Order getOrder(UUID orderId) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM orders WHERE order_id = ? AND (creation_time + duration + ?) > ?")) {
            ps.setString(1, orderId.toString());
            ps.setLong(2, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Order order = buildOrderFromResultSet(rs);
                    if (order.getItem() == null) {
                        Bukkit.getLogger().warning("This is a dev debug message! This order id is NULL: "+order.getOrderId());
                        return null;
                    }

                    if (order.getPlayerName() == null) {
                        String playerName = getPlayerName(order.getCreatorId());
                        if (playerName != null) {
                            order = new Order(
                                    order.getOrderId(),
                                    order.getCreatorId(),
                                    playerName,
                                    order.getItem(),
                                    order.getTotalAmount(),
                                    order.getPricePerItem(),
                                    order.getDeliveredAmount(),
                                    order.getClaimedAmount(),
                                    order.getTotalPaid(),
                                    order.getCreationTime(),
                                    order.getDuration()
                            );

                            queuePlayerNameUpdate(order.getOrderId(), order.getCreatorId(), playerName);
                        }
                    }

                    if (!order.isExpired()) {
                        return order;
                    } else {
                        handleExpiredOrder(order);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public List<Order> getAllOrders() {
        List<Order> orders = new ArrayList<>();
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM orders WHERE (creation_time + duration) > ? " +
                             "ORDER BY (creation_time + duration) DESC")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = buildOrderFromResultSet(rs);
                    if (order.getItem() == null) {
                        Bukkit.getLogger().warning("This is a dev debug message! This order id is NULL: "+order.getOrderId());
                        continue;
                    }

                    if (order.getPlayerName() == null) {
                        String playerName = getPlayerName(order.getCreatorId());
                        if (playerName != null) {
                            order = new Order(
                                    order.getOrderId(),
                                    order.getCreatorId(),
                                    playerName,
                                    order.getItem(),
                                    order.getTotalAmount(),
                                    order.getPricePerItem(),
                                    order.getDeliveredAmount(),
                                    order.getClaimedAmount(),
                                    order.getTotalPaid(),
                                    order.getCreationTime(),
                                    order.getDuration()
                            );

                            queuePlayerNameUpdate(order.getOrderId(), order.getCreatorId(), playerName);
                        }
                    }

                    if (!order.isExpired()) {
                        orders.add(order);
                    } else {
                        handleExpiredOrder(order);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    @Override
    public List<Order> getUnclaimedOrders(UUID playerId) {
        return getPlayerOrders(playerId).stream()
                .filter(order -> order.getClaimedAmount() < order.getDeliveredAmount())
                .toList();
    }

    @Override
    public List<Order> getPlayerOrders(UUID playerId) {
        List<Order> orders = new ArrayList<>();
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM orders WHERE creator_id = ? AND (creation_time + duration + ?) > ? " +
                             "ORDER BY (creation_time + duration) DESC")) {
            ps.setString(1, playerId.toString());
            ps.setLong(2, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(3, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = buildOrderFromResultSet(rs);
                    if (order.getItem() == null) {
                        Bukkit.getLogger().warning("This is a dev debug message! This order id is NULL: "+order.getOrderId());
                        continue;
                    }

                    if (order.getPlayerName() == null) {
                        String playerName = getPlayerName(order.getCreatorId());
                        if (playerName != null) {
                            order = new Order(
                                    order.getOrderId(),
                                    order.getCreatorId(),
                                    playerName,
                                    order.getItem(),
                                    order.getTotalAmount(),
                                    order.getPricePerItem(),
                                    order.getDeliveredAmount(),
                                    order.getClaimedAmount(),
                                    order.getTotalPaid(),
                                    order.getCreationTime(),
                                    order.getDuration()
                            );

                            queuePlayerNameUpdate(order.getOrderId(), order.getCreatorId(), playerName);
                        }
                    }

                    if (!order.isExpired()) {
                        orders.add(order);
                    } else {
                        handleExpiredOrder(order);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    @Override
    public List<Order> getActiveOrders() {
        List<Order> orders = new ArrayList<>();
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT * FROM orders WHERE (creation_time + duration) > ? " +
                             "ORDER BY (creation_time + duration) DESC")) {
            ps.setLong(1, System.currentTimeMillis());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Order order = buildOrderFromResultSet(rs);
                    if (order.getItem() == null) {
                        Bukkit.getLogger().warning("This is a dev debug message! This order id is NULL: "+order.getOrderId());
                        continue;
                    }

                    if (order.getPlayerName() == null) {
                        String playerName = getPlayerName(order.getCreatorId());
                        if (playerName != null) {
                            order = new Order(
                                    order.getOrderId(),
                                    order.getCreatorId(),
                                    playerName,
                                    order.getItem(),
                                    order.getTotalAmount(),
                                    order.getPricePerItem(),
                                    order.getDeliveredAmount(),
                                    order.getClaimedAmount(),
                                    order.getTotalPaid(),
                                    order.getCreationTime(),
                                    order.getDuration()
                            );

                            queuePlayerNameUpdate(order.getOrderId(), order.getCreatorId(), playerName);
                        }
                    }

                    if (!order.isExpired()) {
                        orders.add(order);
                    } else {
                        handleExpiredOrder(order);
                    }
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return orders;
    }

    @Override
    public void updateDeliveredAmount(UUID orderId, long newAmount) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET delivered_amount = ? WHERE order_id = ? " +
                             "AND (creation_time + duration + ?) > ?")) {
            ps.setLong(1, newAmount);
            ps.setString(2, orderId.toString());
            ps.setLong(3, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateTotalPaid(UUID orderId, double newAmount) {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "UPDATE orders SET total_paid = ? WHERE order_id = ? " +
                             "AND (creation_time + duration + ?) > ?")) {
            ps.setDouble(1, newAmount);
            ps.setString(2, orderId.toString());
            ps.setLong(3, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void deleteExpiredOrders() {
        try (Connection conn = mysql.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "DELETE FROM orders WHERE (creation_time + duration + ?) <= ?")) {
            ps.setLong(1, ValuesManager.getInstance().getDeletionThreshold());
            ps.setLong(2, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /*@Override
    public void handleExpiredOrder(Order order) {
        logOrderAction("EXPIRE", order, "Order expired automatically");
        deleteOrder(order.getOrderId());

        OScheduler.sync().run(() -> Bukkit.getPluginManager().callEvent(new OrderExpirationEvent(order)));
    }*/

    // check sqlite for more explanations
    @Override
    public void handleExpiredOrder(Order order) {
        logOrderAction("EXPIRE", order, "Order expired automatically");
        deleteOrderInternal(order.getOrderId());

        OScheduler.sync().run(() -> Bukkit.getPluginManager().callEvent(new OrderExpirationEvent(order)));
    }

    /*@Override
    public byte[] serializeItemStack(ItemStack itemStack) {
        if (itemStack instanceof ItemBuilder) {
            itemStack = ((ItemBuilder) itemStack).toItemStack();
        }

        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            BukkitObjectOutputStream dataOutput = new BukkitObjectOutputStream(outputStream);
            dataOutput.writeObject(itemStack);
            dataOutput.close();

            return Zstd.compress(outputStream.toByteArray());
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    public ItemStack deserializeItemStack(byte[] data) {
        try {
            byte[] decompressedData = Zstd.decompress(data, 1024 * 1024);

            ByteArrayInputStream inputStream = new ByteArrayInputStream(decompressedData);
            BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
            ItemStack item = (ItemStack) dataInput.readObject();
            dataInput.close();
            return item;
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }*/

    private Order buildOrderFromResultSet(ResultSet rs) throws SQLException {
        String playerName = null;
        try {
            playerName = rs.getString("player_name");
        } catch (SQLException ignored) {
        }

        UUID creatorId = UUID.fromString(rs.getString("creator_id"));

        if (playerName == null) {
            playerName = playerNameCache.get(creatorId);
        } else if (playerName != null) {
            playerNameCache.put(creatorId, playerName);
        }

        return new Order(
                UUID.fromString(rs.getString("order_id")),
                creatorId,
                playerName,
                ZStdUtil.deserializeItemStack(rs.getBytes("item_data")),
                rs.getLong("total_amount"),
                rs.getDouble("price_per_item"),
                rs.getLong("delivered_amount"),
                rs.getLong("claimed_amount"),
                rs.getDouble("total_paid"),
                rs.getLong("creation_time"),
                rs.getLong("duration")
        );
    }

    private void migrateIntToBigint(
            Connection conn
    ) {
        try {
            boolean needsMigration = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'total_amount' AND TABLE_SCHEMA = DATABASE()")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && "int".equalsIgnoreCase(rs.getString("DATA_TYPE"))) {
                        needsMigration = true;
                    }
                }
            }

            if (needsMigration) {
                Bukkit.getLogger().info("[DonutOrder] Migrating order amount columns from INT to BIGINT...");
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE orders MODIFY COLUMN total_amount BIGINT NOT NULL");
                    stmt.execute("ALTER TABLE orders MODIFY COLUMN delivered_amount BIGINT NOT NULL");
                    stmt.execute("ALTER TABLE orders MODIFY COLUMN claimed_amount BIGINT NOT NULL");
                }
                Bukkit.getLogger().info("[DonutOrder] Successfully migrated order amount columns to BIGINT.");
            }

            boolean logsNeedsMigration = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = 'order_logs' AND COLUMN_NAME = 'amount' AND TABLE_SCHEMA = DATABASE()")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && "int".equalsIgnoreCase(rs.getString("DATA_TYPE"))) {
                        logsNeedsMigration = true;
                    }
                }
            }

            if (logsNeedsMigration) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE order_logs MODIFY COLUMN amount BIGINT");
                }
                Bukkit.getLogger().info("[DonutOrder] Migrated order_logs amount column to BIGINT.");
            }
        } catch (SQLException e) {
            Bukkit.getLogger().warning("[DonutOrder] Failed to migrate INT to BIGINT: " + e.getMessage());
        }
    }

    public void migrateTextToBlobData() {
        try (Connection conn = mysql.getConnection()) {
            boolean needsMigration = false;
            try (PreparedStatement checkStmt = conn.prepareStatement(
                    "SELECT COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS " +
                            "WHERE TABLE_NAME = 'orders' AND COLUMN_NAME = 'item_data' AND TABLE_SCHEMA = DATABASE()")) {
                try (ResultSet rs = checkStmt.executeQuery()) {
                    if (rs.next() && "text".equalsIgnoreCase(rs.getString("DATA_TYPE"))) {
                        needsMigration = true;
                    }
                }
            }

            if (!needsMigration) {
                return;
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE orders_new (" +
                        "order_id VARCHAR(36) PRIMARY KEY," +
                        "creator_id VARCHAR(36) NOT NULL," +
                        "player_name VARCHAR(36)," +
                        "item_data LONGBLOB NOT NULL," +
                        "total_amount BIGINT NOT NULL," +
                        "price_per_item DOUBLE NOT NULL," +
                        "delivered_amount BIGINT NOT NULL," +
                        "claimed_amount BIGINT NOT NULL," +
                        "total_paid DOUBLE NOT NULL," +
                        "creation_time BIGINT NOT NULL," +
                        "duration BIGINT NOT NULL" +
                        ")");
            }

            List<Order> allOrders = new ArrayList<>();
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT * FROM orders")) {

                while (rs.next()) {
                    String base64Data = rs.getString("item_data");
                    ItemStack item = null;

                    try {
                        ByteArrayInputStream inputStream = new ByteArrayInputStream(Base64Coder.decodeLines(base64Data));
                        BukkitObjectInputStream dataInput = new BukkitObjectInputStream(inputStream);
                        item = (ItemStack) dataInput.readObject();
                        dataInput.close();
                    } catch (Exception e) {
                        Bukkit.getLogger().warning("Failed to deserialize item for order: " + rs.getString("order_id"));
                        continue;
                    }

                    UUID creatorId = UUID.fromString(rs.getString("creator_id"));
                    String playerName = playerNameCache.get(creatorId);

                    Order order = new Order(
                            UUID.fromString(rs.getString("order_id")),
                            creatorId,
                            playerName,
                            item,
                            rs.getLong("total_amount"),
                            rs.getDouble("price_per_item"),
                            rs.getLong("delivered_amount"),
                            rs.getLong("claimed_amount"),
                            rs.getDouble("total_paid"),
                            rs.getLong("creation_time"),
                            rs.getLong("duration")
                    );

                    allOrders.add(order);
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO orders_new (order_id, creator_id, player_name, item_data, total_amount, price_per_item, " +
                            "delivered_amount, claimed_amount, total_paid, creation_time, duration) " +
                            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {

                for (Order order : allOrders) {
                    ps.setString(1, order.getOrderId().toString());
                    ps.setString(2, order.getCreatorId().toString());
                    ps.setString(3, order.getPlayerName());
                    ps.setBytes(4, ZStdUtil.serializeItemStack(order.getItem()));
                    ps.setLong(5, order.getTotalAmount());
                    ps.setDouble(6, order.getPricePerItem());
                    ps.setLong(7, order.getDeliveredAmount());
                    ps.setLong(8, order.getClaimedAmount());
                    ps.setDouble(9, order.getTotalPaid());
                    ps.setLong(10, order.getCreationTime());
                    ps.setLong(11, order.getDuration());
                    ps.executeUpdate();
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP TABLE orders");
                stmt.execute("ALTER TABLE orders_new RENAME TO orders");
            }

            Bukkit.getLogger().info("Successfully migrated " + allOrders.size() + " orders from TEXT to BLOB format");

        } catch (SQLException e) {
            e.printStackTrace();
            Bukkit.getLogger().severe("Failed to migrate database from TEXT to BLOB format");
        }
    }
}