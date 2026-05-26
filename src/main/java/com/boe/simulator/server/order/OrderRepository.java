package com.boe.simulator.server.order;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static java.util.Optional.empty;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.rocksdb.RocksDBException;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.util.SerializationUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class OrderRepository {
    private static final Logger LOGGER = Logger.getLogger(OrderRepository.class.getName());

    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    private static final String CF_ORDERS = RocksDBManager.CF_ORDERS;

    // Write-behind queue: keeps disk I/O off the NewOrder → ACK hot path
    private final LinkedBlockingQueue<Order> writeQueue = new LinkedBlockingQueue<>(1_000_000);
    private volatile boolean asyncRunning;
    private Thread asyncThread;

    public OrderRepository(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
        startAsyncPersistence();
        LOGGER.info("OrderRepository initialized");
    }

    public void save(Order order) {
        try {
            String key = buildKey(order.getClOrdID());
            PersistedOrder persistedOrder = PersistedOrder.fromOrder(order);
            byte[] value = serializer.serialize(persistedOrder);

            dbManager.put(CF_ORDERS, key.getBytes(), value);
            LOGGER.log(Level.FINE, "Saved order: {0}", order.getClOrdID());
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to save order: " + order.getClOrdID(), e);
            throw new RuntimeException("Failed to save order", e);
        }
    }

    // Enqueue order for async write — does not block the calling thread
    public void saveAsync(Order order) {
        if (!writeQueue.offer(order)) {
            save(order); // queue full: sync fallback
        }
    }

    private void startAsyncPersistence() {
        asyncRunning = true;
        asyncThread = Thread.ofVirtual().name("order-persist").start(this::runAsyncWriter);
    }

    public void stopAsyncPersistence() {
        asyncRunning = false;
        if (asyncThread != null) {
            asyncThread.interrupt();
            try { asyncThread.join(5_000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        }
        flushRemaining();
    }

    private void runAsyncWriter() {
        List<Order> batch = new ArrayList<>(256);
        while (asyncRunning) {
            try {
                Order first = writeQueue.poll(1, TimeUnit.MILLISECONDS);
                if (first == null) continue;
                batch.add(first);
                writeQueue.drainTo(batch, 255); // up to 256 per batch flush
                flushBatch(batch);
                batch.clear();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Async persist error", e);
                batch.clear();
            }
        }
        flushRemaining();
    }

    private void flushRemaining() {
        List<Order> remaining = new ArrayList<>();
        writeQueue.drainTo(remaining);
        if (!remaining.isEmpty()) flushBatch(remaining);
    }

    private void flushBatch(List<Order> orders) {
        if (orders.size() == 1) {
            save(orders.get(0));
            return;
        }
        try {
            RocksDBManager.WriteBatchOperation[] ops = new RocksDBManager.WriteBatchOperation[orders.size()];
            for (int i = 0; i < orders.size(); i++) {
                Order o = orders.get(i);
                byte[] key   = buildKey(o.getClOrdID()).getBytes();
                byte[] value = serializer.serialize(PersistedOrder.fromOrder(o));
                ops[i] = new RocksDBManager.WriteBatchOperation(RocksDBManager.OperationType.PUT, CF_ORDERS, key, value);
            }
            dbManager.writeBatch(ops);
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Batch flush failed, falling back to individual saves", e);
            for (Order o : orders) {
                try { save(o); } catch (Exception ex) {
                    LOGGER.log(Level.SEVERE, "Individual save failed for " + o.getClOrdID(), ex);
                }
            }
        }
    }

    public Optional<Order> findByClOrdID(String clOrdID) {
        try {
            String key = buildKey(clOrdID);
            byte[] data = dbManager.get(CF_ORDERS, key.getBytes());

            if (data == null) {
                return Optional.empty();
            }

            PersistedOrder persistedOrder = serializer.deserialize(data, PersistedOrder.class);
            return Optional.of(persistedOrder.toOrder());
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find order: " + clOrdID, e);
            return Optional.empty();
        }
    }

    public Optional<Order> findByOrderID(long orderID) {
        try {
            Map<byte[], byte[]> allData = dbManager.getAll(CF_ORDERS);

            for (byte[] data : allData.values()) {
                PersistedOrder persistedOrder = serializer.deserialize(data, PersistedOrder.class);
                if (persistedOrder.orderID() == orderID) return Optional.of(persistedOrder.toOrder());
            }

            return Optional.empty();
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find order by OrderID: " + orderID, e);
            return empty();
        }
    }

    public List<Order> findByUsername(String username) {
        if (username == null) {
            LOGGER.warning("findByUsername called with null username");
            return new ArrayList<>();
        }

        Map<byte[], byte[]> allData;
        try {
            allData = dbManager.getAll(CF_ORDERS);
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find orders by username: " + username, e);
            return new ArrayList<>();
        }

        List<Order> orders = new ArrayList<>();
        for (Map.Entry<byte[], byte[]> entry : allData.entrySet()) {
            try {
                PersistedOrder persisted = serializer.deserialize(entry.getValue(), PersistedOrder.class);
                if (username.equals(persisted.username())) {
                    orders.add(persisted.toOrder());
                }
            } catch (Exception e) {
                String keyStr = new String(entry.getKey(), StandardCharsets.UTF_8);
                LOGGER.log(Level.WARNING, "Failed to deserialize order: {0}, skipping...", keyStr);
            }
        }

        LOGGER.log(Level.FINE, "Found {0} orders for user: {1}", new Object[]{orders.size(), username});
        return orders;
    }

    public List<Order> findActiveOrders() {
        Map<byte[], byte[]> allData;
        try {
            allData = dbManager.getAll(CF_ORDERS);
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to get orders from database", e);
            return new ArrayList<>();
        }

        List<Order> activeOrders = new ArrayList<>();
        for (Map.Entry<byte[], byte[]> entry : allData.entrySet()) {
            try {
                PersistedOrder persistedOrder = serializer.deserialize(entry.getValue(), PersistedOrder.class);
                Order order = persistedOrder.toOrder();
                if (order.isLive()) activeOrders.add(order);
            } catch (Exception e) {
                String key = new String(entry.getKey(), StandardCharsets.UTF_8);
                LOGGER.log(Level.WARNING, "Failed to deserialize order: " + key + ", skipping...", e);
            }
        }

        LOGGER.log(Level.INFO, "Loaded {0} active orders from database", activeOrders.size());
        return activeOrders;
    }

    public List<Order> findActiveOrdersByClearingFirm(String clearingFirm) {
        return findActiveOrders().stream()
                .filter(o -> clearingFirm.equals(o.getClearingFirm()))
                .collect(Collectors.toList());
    }

    public List<Order> findActiveOrdersBySymbol(String symbol) {
        return findActiveOrders().stream()
                .filter(o -> symbol.equals(o.getSymbol()))
                .collect(Collectors.toList());
    }

    public void delete(String clOrdID) {
        try {
            String key = buildKey(clOrdID);
            dbManager.delete(CF_ORDERS, key.getBytes());
            LOGGER.log(Level.INFO, "Deleted order: {0}", clOrdID);
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete order: " + clOrdID, e);
            throw new RuntimeException("Failed to delete order", e);
        }
    }

    public boolean existsByClOrdID(String clOrdID) {
        try {
            String key = buildKey(clOrdID);
            return dbManager.exists(CF_ORDERS, key.getBytes());
        } catch (RocksDBException | IllegalArgumentException e) {
            LOGGER.log(Level.WARNING, "Failed to check if order exists: " + clOrdID, e);
            return false;
        }
    }

    public long count() {
        try {
            Map<byte[], byte[]> allData = dbManager.getAll(CF_ORDERS);
            return allData.size();
        } catch (RocksDBException e) {
            LOGGER.log(Level.WARNING, "Failed to count orders", e);
            return 0;
        }
    }

    public long countActive() {
        return findActiveOrders().size();
    }

    private String buildKey(String clOrdID) {
        return "order:" + clOrdID;
    }

    private record PersistedOrder(
            @JsonProperty("clOrdID") String clOrdID,
            @JsonProperty("orderID") long orderID,
            @JsonProperty("sessionSubID") String sessionSubID,
            @JsonProperty("username") String username,
            @JsonProperty("side") byte side,
            @JsonProperty("orderQty") int orderQty,
            @JsonProperty("leavesQty") int leavesQty,
            @JsonProperty("cumQty") int cumQty,
            @JsonProperty("price") String price,
            @JsonProperty("ordType") byte ordType,
            @JsonProperty("symbol") String symbol,
            @JsonProperty("maturityDate") String maturityDate,
            @JsonProperty("strikePrice") String strikePrice,
            @JsonProperty("putOrCall") byte putOrCall,
            @JsonProperty("capacity") byte capacity,
            @JsonProperty("account") String account,
            @JsonProperty("clearingFirm") String clearingFirm,
            @JsonProperty("clearingAccount") String clearingAccount,
            @JsonProperty("openClose") byte openClose,
            @JsonProperty("state") String state,
            @JsonProperty("createdAt") String createdAt,
            @JsonProperty("lastModified") String lastModified,
            @JsonProperty("routingInst") byte routingInst,
            @JsonProperty("receivedSequence") int receivedSequence,
            @JsonProperty("lastSentSequence") int lastSentSequence
    ) {

        @JsonCreator
        public PersistedOrder {
        }

        static PersistedOrder fromOrder(Order order) {
            return new PersistedOrder(
                    order.getClOrdID(),
                    order.getOrderID(),
                    order.getSessionSubID(),
                    order.getUsername(),
                    order.getSide(),
                    order.getOrderQty(),
                    order.getLeavesQty(),
                    order.getCumQty(),
                    order.getPrice() != null ? order.getPrice().toString() : null,
                    order.getOrdType(),
                    order.getSymbol(),
                    order.getMaturityDate() != null ? order.getMaturityDate().toString() : null,
                    order.getStrikePrice() != null ? order.getStrikePrice().toString() : null,
                    order.getPutOrCall(),
                    order.getCapacity(),
                    order.getAccount(),
                    order.getClearingFirm(),
                    order.getClearingAccount(),
                    order.getOpenClose(),
                    order.getState().name(),
                    order.getCreatedAt().toString(),
                    order.getLastModified().toString(),
                    order.getRoutingInst(),
                    order.getReceivedSequence(),
                    order.getLastSentSequence()
            );
        }

        Order toOrder() {
            Order.Builder builder = Order.builder()
                    .clOrdID(clOrdID)
                    .orderID(orderID)
                    .sessionSubID(sessionSubID)
                    .username(username)
                    .side(side)
                    .orderQty(orderQty)
                    .ordType(ordType)
                    .symbol(symbol)
                    .capacity(capacity)
                    .account(account)
                    .clearingFirm(clearingFirm)
                    .clearingAccount(clearingAccount)
                    .openClose(openClose)
                    .routingInst(routingInst)
                    .receivedSequence(receivedSequence);

            if (price != null) builder.price(new BigDecimal(price));


            if (maturityDate != null) builder.maturityDate(Instant.parse(maturityDate));


            if (strikePrice != null) builder.strikePrice(new BigDecimal(strikePrice));


            builder.putOrCall(putOrCall);

            Order order = builder.build();

            // Restore state (using reflection to bypass state machine)
            try {
                java.lang.reflect.Field stateField = Order.class.getDeclaredField("state");
                stateField.setAccessible(true);
                stateField.set(order, OrderState.valueOf(state));

                java.lang.reflect.Field leavesQtyField = Order.class.getDeclaredField("leavesQty");
                leavesQtyField.setAccessible(true);
                leavesQtyField.set(order, leavesQty);

                java.lang.reflect.Field cumQtyField = Order.class.getDeclaredField("cumQty");
                cumQtyField.setAccessible(true);
                cumQtyField.set(order, cumQty);

                java.lang.reflect.Field lastModifiedField = Order.class.getDeclaredField("lastModified");
                lastModifiedField.setAccessible(true);
                lastModifiedField.set(order, Instant.parse(lastModified));

                order.setLastSentSequence(lastSentSequence);
            } catch (IllegalAccessException | IllegalArgumentException | NoSuchFieldException | SecurityException e) {
                LOGGER.log(Level.WARNING, "Failed to restore order state", e);
            }

            return order;
        }
    }
}