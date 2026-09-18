package me.rein.donutorder.commons.manager;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import me.rein.donutorder.commons.enums.ConfigSelector;
import me.rein.donutorder.commons.scheduler.OScheduler;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.params.SetParams;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class OrderAntiDupeManager {

    @Getter(lazy = true)
    private static final OrderAntiDupeManager instance = new OrderAntiDupeManager();

    private JedisPool jedisPool;
    private boolean useRedis = false;
    private static final String LOCK_PREFIX = "donutorder:lock:";
    private static final int LOCK_EXPIRY = 30;

    private final Set<UUID> lockedOrders = Collections.synchronizedSet(new HashSet<>());
    private final Map<UUID, OrderOperation> orderOperations = new ConcurrentHashMap<>();

    private final Map<String, List<Long>> cancellationAttempts = new HashMap<>();
    private final Map<String, List<Long>> deliveryAttempts = new HashMap<>();
    private static final int MAX_RAPID_ATTEMPTS = 3;
    private static final long ATTEMPT_WINDOW = 5000;

    public enum OrderOperation {
        NONE,
        DELIVERING,
        CANCELLING,
        COMPLETED,
        CANCELLED
    }

    public void initRedis(String host, int port, String password, boolean useSSL) {
        try {
            JedisPoolConfig poolConfig = new JedisPoolConfig();
            poolConfig.setMaxTotal(128);
            poolConfig.setMaxIdle(16);
            poolConfig.setMinIdle(8);

            if (password != null && !password.isEmpty()) {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, password, useSSL);
            } else {
                jedisPool = new JedisPool(poolConfig, host, port, 2000, useSSL);
            }

            try (Jedis jedis = jedisPool.getResource()) {
                jedis.ping();
                useRedis = true;
                Bukkit.getLogger().info("[DonutOrder] Successfully connected to Redis");
            }
        } catch (Exception e) {
            useRedis = false;
            Bukkit.getLogger().warning("[DonutOrder] Failed to connect to Redis: " + e.getMessage());
            Bukkit.getLogger().warning("[DonutOrder] Falling back to in-memory locking system");
        }
    }

    public boolean lockOrder(UUID orderId) {
        if (useRedis) {
            return lockOrderRedis(orderId);
        } else {
            synchronized (lockedOrders) {
                if (lockedOrders.contains(orderId)) {
                    return false;
                }
                lockedOrders.add(orderId);
                return true;
            }
        }
    }

    private boolean lockOrderRedis(UUID orderId) {
        String lockKey = LOCK_PREFIX + orderId.toString();

        try (Jedis jedis = jedisPool.getResource()) {
            String result = jedis.set(lockKey, "1", SetParams.setParams().nx().ex(LOCK_EXPIRY));
            return "OK".equals(result);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DonutOrder] Redis lock error: " + e.getMessage());
            synchronized (lockedOrders) {
                if (lockedOrders.contains(orderId)) {
                    return false;
                }
                lockedOrders.add(orderId);
                return true;
            }
        }
    }

    public void unlockOrder(UUID orderId) {
        if (useRedis) {
            unlockOrderRedis(orderId);
        } else {
            synchronized (lockedOrders) {
                lockedOrders.remove(orderId);
            }
        }
    }

    private void unlockOrderRedis(UUID orderId) {
        String lockKey = LOCK_PREFIX + orderId.toString();

        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(lockKey);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DonutOrder] Redis unlock error: " + e.getMessage());
            synchronized (lockedOrders) {
                lockedOrders.remove(orderId);
            }
        }
    }

    public boolean isOrderLocked(UUID orderId) {
        if (useRedis) {
            return isOrderLockedRedis(orderId);
        } else {
            synchronized (lockedOrders) {
                return lockedOrders.contains(orderId);
            }
        }
    }

    private boolean isOrderLockedRedis(UUID orderId) {
        String lockKey = LOCK_PREFIX + orderId.toString();

        try (Jedis jedis = jedisPool.getResource()) {
            return jedis.exists(lockKey);
        } catch (Exception e) {
            Bukkit.getLogger().warning("[DonutOrder] Redis check lock error: " + e.getMessage());
            synchronized (lockedOrders) {
                return lockedOrders.contains(orderId);
            }
        }
    }

    public boolean beginDelivery(UUID orderId) {
        if (!lockOrder(orderId)) {
            return false;
        }

        OrderOperation current = orderOperations.putIfAbsent(orderId, OrderOperation.DELIVERING);

        if (current != null && current != OrderOperation.NONE) {
            unlockOrder(orderId);
            return false;
        }

        return true;
    }

    public boolean beginCancel(UUID orderId) {
        if (!lockOrder(orderId)) {
            return false;
        }

        OrderOperation current = orderOperations.putIfAbsent(orderId, OrderOperation.CANCELLING);

        if (current != null && current != OrderOperation.NONE) {
            unlockOrder(orderId);
            return false;
        }

        return true;
    }

    public void completeOperation(UUID orderId, OrderOperation finalState) {
        orderOperations.put(orderId, finalState);
        unlockOrder(orderId);
    }

    public void abortOperation(UUID orderId) {
        orderOperations.remove(orderId);
        unlockOrder(orderId);
    }

    public boolean isOrderBeingDelivered(UUID orderId) {
        OrderOperation op = orderOperations.get(orderId);
        return op == OrderOperation.DELIVERING;
    }

    public boolean isOrderBeingCancelled(UUID orderId) {
        OrderOperation op = orderOperations.get(orderId);
        return op == OrderOperation.CANCELLING;
    }

    public boolean isOrderFinalized(UUID orderId) {
        OrderOperation op = orderOperations.get(orderId);
        return op == OrderOperation.COMPLETED || op == OrderOperation.CANCELLED;
    }

    public boolean detectSuspiciousActivity(UUID playerId, UUID orderId, boolean isDelivery) {
        String key = playerId.toString() + ":" + orderId.toString();
        long now = System.currentTimeMillis();

        Map<String, List<Long>> attemptsMap = isDelivery ? deliveryAttempts : cancellationAttempts;

        synchronized (attemptsMap) {
            List<Long> attempts = attemptsMap.getOrDefault(key, new ArrayList<>());

            attempts.removeIf(time -> now - time > ATTEMPT_WINDOW);

            attempts.add(now);

            attemptsMap.put(key, attempts);

            if (attempts.size() >= MAX_RAPID_ATTEMPTS) {
                Bukkit.getLogger().warning("[DonutOrder] Potential duplication attempt by " +
                        playerId + " for order " + orderId + " with " + attempts.size() +
                        " " + (isDelivery ? "delivery" : "cancellation") + " attempts in " +
                        ATTEMPT_WINDOW/1000 + " seconds!");
                return true;
            }
        }

        return false;
    }

    public void cleanupActivityTracking() {
        long now = System.currentTimeMillis();

        synchronized (cancellationAttempts) {
            cancellationAttempts.entrySet().removeIf(entry ->
                    entry.getValue().stream().allMatch(time -> now - time > ATTEMPT_WINDOW * 2));
        }

        synchronized (deliveryAttempts) {
            deliveryAttempts.entrySet().removeIf(entry ->
                    entry.getValue().stream().allMatch(time -> now - time > ATTEMPT_WINDOW * 2));
        }

        orderOperations.entrySet().removeIf(entry -> {
            OrderOperation op = entry.getValue();
            return (op == OrderOperation.COMPLETED || op == OrderOperation.CANCELLED) &&
                    !OrderManager.getInstance().getOrders().containsKey(entry.getKey());
        });

        if (!useRedis) {
            synchronized (lockedOrders) {
                for (UUID orderId : new HashSet<>(lockedOrders)) {
                    if (!OrderManager.getInstance().getOrders().containsKey(orderId)) {
                        Bukkit.getLogger().warning("[DonutOrder] Removing stale lock for non-existent order: " + orderId);
                        lockedOrders.remove(orderId);
                        orderOperations.remove(orderId);
                    }
                }
            }
        }
    }

    public void load() {
        FileConfiguration config = ConfigSelector.DATABASES.getConfig();

        if (config.getBoolean("redis.enabled", false)) {
            String host = config.getString("redis.host", "localhost");
            int port = config.getInt("redis.port", 6379);
            String password = config.getString("redis.password", "");
            if (password.isEmpty()) password = null;
            boolean useSSL = config.getBoolean("redis.useSSL", false);

            initRedis(host, port, password, useSSL);
        }

        OScheduler.async().runRepeating(this::cleanupActivityTracking, 20 * 60 * 5);
    }

    public void close() {
        if (jedisPool != null && !jedisPool.isClosed()) {
            jedisPool.close();
        }
    }
}
//package me.rein.donutorder.manager;
//
//import me.rein.donutorder.DonutOrder;
//import me.rein.donutorder.enums.ConfigSelector;
//import me.rein.donutorder.scheduler.OScheduler;
//import org.bukkit.Bukkit;
//import org.bukkit.configuration.file.FileConfiguration;
//import redis.clients.jedis.Jedis;
//import redis.clients.jedis.JedisPool;
//import redis.clients.jedis.JedisPoolConfig;
//import redis.clients.jedis.params.SetParams;
//
//import java.util.*;
//
//public class OrderAntiDupeManager {
//    private static class LazyHolder {
//        public static final OrderAntiDupeManager INSTANCE = new OrderAntiDupeManager();
//    }
//
//    public static OrderAntiDupeManager getInstance() {
//        return OrderAntiDupeManager.LazyHolder.INSTANCE;
//    }
//
//    private OrderAntiDupeManager() {}
//
//    // Redis configuration
//    private JedisPool jedisPool;
//    private boolean useRedis = false;
//    private static final String LOCK_PREFIX = "donutorder:lock:";
//    private static final int LOCK_EXPIRY = 30; // seconds
//
//    // Fallback in-memory lock system
//    private final Set<UUID> lockedOrders = Collections.synchronizedSet(new HashSet<>());
//
//    private final Map<String, List<Long>> cancellationAttempts = new HashMap<>();
//    private final Map<String, List<Long>> deliveryAttempts = new HashMap<>();
//    private static final int MAX_RAPID_ATTEMPTS = 3;
//    private static final long ATTEMPT_WINDOW = 5000; // 5 seconds
//
//    /**
//     * Initialize Redis connection
//     * @param host Redis host
//     * @param port Redis port
//     * @param password Redis password (can be null)
//     * @param useSSL Whether to use SSL for Redis connection
//     */
//    public void initRedis(String host, int port, String password, boolean useSSL) {
//        try {
//            JedisPoolConfig poolConfig = new JedisPoolConfig();
//            poolConfig.setMaxTotal(128);
//            poolConfig.setMaxIdle(16);
//            poolConfig.setMinIdle(8);
//
//            if (password != null && !password.isEmpty()) {
//                jedisPool = new JedisPool(poolConfig, host, port, 2000, password, useSSL);
//            } else {
//                jedisPool = new JedisPool(poolConfig, host, port, 2000, useSSL);
//            }
//
//            // Test connection
//            try (Jedis jedis = jedisPool.getResource()) {
//                jedis.ping();
//                useRedis = true;
//                Bukkit.getLogger().info("[DonutOrder] Successfully connected to Redis");
//            }
//        } catch (Exception e) {
//            useRedis = false;
//            Bukkit.getLogger().warning("[DonutOrder] Failed to connect to Redis: " + e.getMessage());
//            Bukkit.getLogger().warning("[DonutOrder] Falling back to in-memory locking system");
//        }
//    }
//
//    /**
//     * Attempts to lock an order for processing
//     * @param orderId The UUID of the order to lock
//     * @return true if the order was successfully locked, false if it was already locked
//     */
//    public boolean lockOrder(UUID orderId) {
//        if (useRedis) {
//            return lockOrderRedis(orderId);
//        } else {
//            // Fall back to in-memory locking
//            synchronized (lockedOrders) {
//                if (lockedOrders.contains(orderId)) {
//                    return false;
//                }
//                lockedOrders.add(orderId);
//                return true;
//            }
//        }
//    }
//
//    private boolean lockOrderRedis(UUID orderId) {
//        String lockKey = LOCK_PREFIX + orderId.toString();
//
//        try (Jedis jedis = jedisPool.getResource()) {
//            // Try to set the key with NX (only if it doesn't exist) and EX (expiry in seconds)
//            String result = jedis.set(lockKey, "1", SetParams.setParams().nx().ex(LOCK_EXPIRY));
//            return "OK".equals(result);
//        } catch (Exception e) {
//            Bukkit.getLogger().warning("[DonutOrder] Redis lock error: " + e.getMessage());
//            // Fall back to in-memory if Redis fails
//            synchronized (lockedOrders) {
//                if (lockedOrders.contains(orderId)) {
//                    return false;
//                }
//                lockedOrders.add(orderId);
//                return true;
//            }
//        }
//    }
//
//    /**
//     * Unlocks an order that was previously locked
//     * @param orderId The UUID of the order to unlock
//     */
//    public void unlockOrder(UUID orderId) {
//        if (useRedis) {
//            unlockOrderRedis(orderId);
//        } else {
//            synchronized (lockedOrders) {
//                lockedOrders.remove(orderId);
//            }
//        }
//    }
//
//    private void unlockOrderRedis(UUID orderId) {
//        String lockKey = LOCK_PREFIX + orderId.toString();
//
//        try (Jedis jedis = jedisPool.getResource()) {
//            jedis.del(lockKey);
//        } catch (Exception e) {
//            Bukkit.getLogger().warning("[DonutOrder] Redis unlock error: " + e.getMessage());
//            // Also remove from in-memory as a fallback
//            synchronized (lockedOrders) {
//                lockedOrders.remove(orderId);
//            }
//        }
//    }
//
//    /**
//     * Checks if an order is currently locked (being processed)
//     * @param orderId The UUID of the order to check
//     * @return true if the order is locked, false otherwise
//     */
//    public boolean isOrderLocked(UUID orderId) {
//        if (useRedis) {
//            return isOrderLockedRedis(orderId);
//        } else {
//            synchronized (lockedOrders) {
//                return lockedOrders.contains(orderId);
//            }
//        }
//    }
//
//    private boolean isOrderLockedRedis(UUID orderId) {
//        String lockKey = LOCK_PREFIX + orderId.toString();
//
//        try (Jedis jedis = jedisPool.getResource()) {
//            return jedis.exists(lockKey);
//        } catch (Exception e) {
//            Bukkit.getLogger().warning("[DonutOrder] Redis check lock error: " + e.getMessage());
//            // Fall back to in-memory if Redis fails
//            synchronized (lockedOrders) {
//                return lockedOrders.contains(orderId);
//            }
//        }
//    }
//
//    /**
//     * Detects suspicious rapid order operations that might indicate duplication attempts
//     * @param playerId The player's UUID
//     * @param orderId The order's UUID
//     * @param isDelivery Whether this is a delivery operation (true) or cancel operation (false)
//     * @return true if suspicious activity detected, false otherwise
//     */
//    public boolean detectSuspiciousActivity(UUID playerId, UUID orderId, boolean isDelivery) {
//        String key = playerId.toString() + ":" + orderId.toString();
//        long now = System.currentTimeMillis();
//
//        Map<String, List<Long>> attemptsMap = isDelivery ? deliveryAttempts : cancellationAttempts;
//
//        synchronized (attemptsMap) {
//            List<Long> attempts = attemptsMap.getOrDefault(key, new ArrayList<>());
//
//            attempts.removeIf(time -> now - time > ATTEMPT_WINDOW);
//
//            attempts.add(now);
//
//            attemptsMap.put(key, attempts);
//
//            if (attempts.size() >= MAX_RAPID_ATTEMPTS) {
//                Bukkit.getLogger().warning("[DonutOrder] Potential duplication attempt by " +
//                        playerId + " for order " + orderId + " with " + attempts.size() +
//                        " " + (isDelivery ? "delivery" : "cancellation") + " attempts in " +
//                        ATTEMPT_WINDOW/1000 + " seconds!");
//                return true;
//            }
//        }
//
//        return false;
//    }
//
//    /**
//     * Cleanup method to be called periodically to prevent memory leaks
//     * Should be called every few minutes
//     */
//    public void cleanupActivityTracking() {
//        long now = System.currentTimeMillis();
//
//        synchronized (cancellationAttempts) {
//            cancellationAttempts.entrySet().removeIf(entry ->
//                    entry.getValue().stream().allMatch(time -> now - time > ATTEMPT_WINDOW * 2));
//        }
//
//        synchronized (deliveryAttempts) {
//            deliveryAttempts.entrySet().removeIf(entry ->
//                    entry.getValue().stream().allMatch(time -> now - time > ATTEMPT_WINDOW * 2));
//        }
//
//        // We don't need to clean up Redis locks as they have an automatic expiry
//        // But clean up in-memory locks for non-Redis mode
//        if (!useRedis) {
//            synchronized (lockedOrders) {
//                for (UUID orderId : new HashSet<>(lockedOrders)) {
//                    if (!OrderManager.getInstance().getOrders().containsKey(orderId)) {
//                        Bukkit.getLogger().warning("[DonutOrder] Removing stale lock for non-existent order: " + orderId);
//                        lockedOrders.remove(orderId);
//                    }
//                }
//            }
//        }
//    }
//
//    public void load() {
//        FileConfiguration config = ConfigSelector.DATABASES.getConfig();
//
//        if (config.getBoolean("redis.enabled", false)) {
//            String host = config.getString("redis.host", "localhost");
//            int port = config.getInt("redis.port", 6379);
//            String password = config.getString("redis.password", "");
//            if (password.isEmpty()) password = null;
//            boolean useSSL = config.getBoolean("redis.useSSL", false);
//
//            initRedis(host, port, password, useSSL);
//        }
//
//        OScheduler.async().runRepeating(this::cleanupActivityTracking, 20 * 60 * 5);
//    }
//
//    public void close() {
//        if (jedisPool != null && !jedisPool.isClosed()) {
//            jedisPool.close();
//        }
//    }
//}
