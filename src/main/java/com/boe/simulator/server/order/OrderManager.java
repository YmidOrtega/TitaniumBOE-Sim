package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.*;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.session.ClientSession;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderManager {
    private static final Logger LOGGER = Logger.getLogger(OrderManager.class.getName());

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;

    // In-memory cache de órdenes activas para acceso rápido
    private final ConcurrentHashMap<String, Order> activeOrdersByClOrdID;
    private final ConcurrentHashMap<Long, Order> activeOrdersByOrderID;

    // Generador de OrderID
    private final AtomicLong orderIDGenerator;

    // Statistics
    private final AtomicLong totalOrdersReceived;
    private final AtomicLong totalOrdersAccepted;
    private final AtomicLong totalOrdersRejected;
    private final AtomicLong totalOrdersCancelled;

    public OrderManager(RocksDBManager dbManager) {
        this.orderRepository = new OrderRepository(dbManager);
        this.orderValidator = new OrderValidator();
        this.activeOrdersByClOrdID = new ConcurrentHashMap<>();
        this.activeOrdersByOrderID = new ConcurrentHashMap<>();
        this.orderIDGenerator = new AtomicLong(1000000); // Start from 1M

        this.totalOrdersReceived = new AtomicLong(0);
        this.totalOrdersAccepted = new AtomicLong(0);
        this.totalOrdersRejected = new AtomicLong(0);
        this.totalOrdersCancelled = new AtomicLong(0);

        // Load active orders from database on startup
        loadActiveOrders();

        LOGGER.info("OrderManager initialized");
    }

    public OrderResponse processNewOrder(NewOrderMessage message, ClientSession session) {
        totalOrdersReceived.incrementAndGet();

        LOGGER.log(Level.INFO, "[Session {0}] Processing NewOrder: {1}", new Object[]{
                session.getConnectionId(), message.getClOrdID()
        });

        // 1. Validar mensaje
        OrderValidator.ValidationResult validation = orderValidator.validateNewOrder(message);
        if (!validation.isValid()) {
            LOGGER.log(Level.WARNING, "[Session {0}] Order rejected - validation failed: {1}",
                    new Object[]{session.getConnectionId(), validation.getErrorMessage()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_MISSING_REQUIRED_FIELD,
                    validation.getErrorMessage()
            );
        }

        // 2. Verificar duplicado de ClOrdID
        if (activeOrdersByClOrdID.containsKey(message.getClOrdID())) {
            LOGGER.log(Level.WARNING, "[Session {0}] Order rejected - duplicate ClOrdID: {1}", new Object[]{
                    session.getConnectionId(), message.getClOrdID()
            });
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_DUPLICATE_CLORDID,
                    "Duplicate ClOrdID: " + message.getClOrdID()
            );
        }

        // 3. Crear orden
        try {
            long orderID = orderIDGenerator.getAndIncrement();

            Order order = Order.builder()
                    .clOrdID(message.getClOrdID())
                    .orderID(orderID)
                    .sessionSubID(session.getSessionSubID())
                    .username(session.getUsername())
                    .side(message.getSide())
                    .orderQty(message.getOrderQty())
                    .price(message.getPrice())
                    .ordType(message.getOrdType() != 0 ? message.getOrdType() : (byte)2) // Default: Limit
                    .symbol(message.getSymbol())
                    .maturityDate(message.getMaturityDate())
                    .strikePrice(message.getStrikePrice())
                    .putOrCall(message.getPutOrCall())
                    .capacity(message.getCapacity())
                    .account(message.getAccount() != null ? message.getAccount() : "")
                    .clearingFirm(message.getClearingFirm() != null ? message.getClearingFirm() : "")
                    .clearingAccount(message.getClearingAccount() != null ? message.getClearingAccount() : "")
                    .openClose(message.getOpenClose() != 0 ? message.getOpenClose() : (byte)'N')
                    .routingInst(message.getRoutingInst() != 0 ? message.getRoutingInst() : (byte)'B')
                    .receivedSequence(message.getSequenceNumber())
                    .build();

            // 4. Acknowledge orden
            order.acknowledge();

            // 5. Guardar en repositorio y cache
            orderRepository.save(order);
            activeOrdersByClOrdID.put(order.getClOrdID(), order);
            activeOrdersByOrderID.put(order.getOrderID(), order);

            totalOrdersAccepted.incrementAndGet();

            LOGGER.log(Level.INFO, "[Session {0}] Order accepted: {1} (OrderID: {2})", new Object[]{
                    session.getConnectionId(), order.getClOrdID(), order.getOrderID()
            });

            return OrderResponse.acknowledged(order);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error processing order", e);
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_UNKNOWN_ERROR,
                    "Internal error: " + e.getMessage()
            );
        }
    }

    public CancelResponse processCancelOrder(CancelOrderMessage message, ClientSession session) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing CancelOrder: {1}", new Object[]{
                session.getConnectionId(), message
        });

        // Verificar si es mass cancel
        if (message.isMassCancel()) return processMassCancel(message, session);

        // Single order cancel
        return processSingleCancel(message.getOrigClOrdID(), session);
    }

    private CancelResponse processSingleCancel(String origClOrdID, ClientSession session) {

        Order order = activeOrdersByClOrdID.get(origClOrdID);

        if (order == null) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - order not found: {1}", new Object[]{
                    session.getConnectionId(), origClOrdID
            });
            return CancelResponse.rejected(
                    origClOrdID,
                    "Order not found or already terminated"
            );
        }

        // 2. Verificar permisos (usuario debe ser el dueño)
        if (!order.getUsername().equals(session.getUsername())) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - unauthorized: {1}",
                    new Object[]{session.getConnectionId(), origClOrdID});
            return CancelResponse.rejected(
                    origClOrdID,
                    "Unauthorized: order belongs to different user"
            );
        }

        // 3. Verificar estado
        if (!order.getState().isCancellable()) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - not cancellable: {1} (state: {2})", new Object[]{
                            session.getConnectionId(), origClOrdID, order.getState()
            });
            return CancelResponse.rejected(
                    origClOrdID,
                    "Order not cancellable in state: " + order.getState()
            );
        }

        // 4. Cancelar orden
        try {
            order.cancel();
            orderRepository.save(order);

            // Remover del cache de órdenes activas
            activeOrdersByClOrdID.remove(order.getClOrdID());
            activeOrdersByOrderID.remove(order.getOrderID());

            totalOrdersCancelled.incrementAndGet();

            LOGGER.log(Level.INFO, "[Session {0}] Order cancelled: {1}", new Object[]{
                    session.getConnectionId(), origClOrdID
            });

            return CancelResponse.cancelled(order, OrderCancelledMessage.REASON_USER_REQUESTED);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[Session " + session.getConnectionId() + "] Error cancelling order", e);
            return CancelResponse.rejected(
                    origClOrdID,
                    "Internal error: " + e.getMessage()
            );
        }
    }

    private CancelResponse processMassCancel(CancelOrderMessage message, ClientSession session) {
        LOGGER.log(Level.INFO, "[Session {0}] Processing Mass Cancel: type={1}, firm={2}, root={3}", new Object[]{
                session.getConnectionId(),
                message.getMassCancelType(),
                message.getClearingFirm(),
                message.getRiskRoot()
        });

        List<Order> ordersToCancel = switch (message.getMassCancelType()) {
            case FIRM -> filterOrdersByClearingFirm(message.getClearingFirm(), session);
            case SYMBOL -> filterOrdersBySymbol(message.getRiskRoot(), session);
            case ALL -> filterAllOrders(session);
            default -> List.of();
        };

        List<String> clOrdIDsToRemove = new ArrayList<>();
        List<Long> orderIDsToRemove = new ArrayList<>();
        int cancelledCount = 0;

        // Cancelar órdenes
        for (Order order : ordersToCancel) {
            try {
                order.cancel();
                orderRepository.save(order);
                clOrdIDsToRemove.add(order.getClOrdID());
                orderIDsToRemove.add(order.getOrderID());
                cancelledCount++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cancel order: " + order.getClOrdID(), e);
            }
        }

        for (int i = 0; i < clOrdIDsToRemove.size(); i++) {
            activeOrdersByClOrdID.remove(clOrdIDsToRemove.get(i));
            activeOrdersByOrderID.remove(orderIDsToRemove.get(i));
        }

        totalOrdersCancelled.addAndGet(cancelledCount);

        LOGGER.log(Level.INFO, "[Session {0}] Mass Cancel completed: {1} orders cancelled", new Object[]{
                session.getConnectionId(),
                cancelledCount}
        );

        return CancelResponse.massCancelled(cancelledCount, message.getMassCancelId());
    }

    private List<Order> filterOrdersByClearingFirm(String clearingFirm, ClientSession session) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(session.getUsername()))
                .filter(o -> clearingFirm.equals(o.getClearingFirm()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private List<Order> filterOrdersBySymbol(String symbol, ClientSession session) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(session.getUsername()))
                .filter(o -> symbol.equals(o.getSymbol()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private List<Order> filterAllOrders(ClientSession session) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(session.getUsername()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private void loadActiveOrders() {
        try {
            List<Order> activeOrders = orderRepository.findActiveOrders();
            for (Order order : activeOrders) {
                activeOrdersByClOrdID.put(order.getClOrdID(), order);
                activeOrdersByOrderID.put(order.getOrderID(), order);
            }
            LOGGER.log(Level.INFO, "Loaded {0} active orders from database", activeOrders.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load active orders", e);
        }
    }

    // Getters para statistics
    public long getTotalOrdersReceived() { return totalOrdersReceived.get(); }
    public long getTotalOrdersAccepted() { return totalOrdersAccepted.get(); }
    public long getTotalOrdersRejected() { return totalOrdersRejected.get(); }
    public long getTotalOrdersCancelled() { return totalOrdersCancelled.get(); }
    public int getActiveOrderCount() { return activeOrdersByClOrdID.size(); }

    public Optional<Order> findByClOrdID(String clOrdID) {
        Order order = activeOrdersByClOrdID.get(clOrdID);
        if (order != null) {
            return Optional.of(order);
        }
        return orderRepository.findByClOrdID(clOrdID);
    }

    public void printStatistics() {
        LOGGER.info("========== Order Statistics ==========");
        LOGGER.log(Level.INFO, "Total Received: {0}", totalOrdersReceived.get());
        LOGGER.log(Level.INFO, "Total Accepted: {0}", totalOrdersAccepted.get());
        LOGGER.log(Level.INFO, "Total Rejected: {0}", totalOrdersRejected.get());
        LOGGER.log(Level.INFO, "Total Cancelled: {0}", totalOrdersCancelled.get());
        LOGGER.log(Level.INFO, "Active Orders: {0}", activeOrdersByClOrdID.size());
        LOGGER.info("=====================================");
    }

    // Response classes
    public static class OrderResponse {
        private final ResponseType type;
        private final Order order;
        private final String clOrdID;
        private final byte rejectReason;
        private final String rejectText;

        private OrderResponse(ResponseType type, Order order, String clOrdID, byte rejectReason, String rejectText) {
            this.type = type;
            this.order = order;
            this.clOrdID = clOrdID;
            this.rejectReason = rejectReason;
            this.rejectText = rejectText;
        }

        public static OrderResponse acknowledged(Order order) {
            return new OrderResponse(ResponseType.ACKNOWLEDGED, order, null, (byte)0, null);
        }

        public static OrderResponse rejected(String clOrdID, byte reason, String text) {
            return new OrderResponse(ResponseType.REJECTED, null, clOrdID, reason, text);
        }

        public boolean isAcknowledged() { return type == ResponseType.ACKNOWLEDGED; }
        public boolean isRejected() { return type == ResponseType.REJECTED; }
        public Order getOrder() { return order; }
        public String getClOrdID() { return clOrdID; }
        public byte getRejectReason() { return rejectReason; }
        public String getRejectText() { return rejectText; }

        enum ResponseType { ACKNOWLEDGED, REJECTED }
    }

    public static class CancelResponse {
        private final ResponseType type;
        private final Order order;
        private final String clOrdID;
        private final byte cancelReason;
        private final String rejectText;
        private final int massCancelCount;
        private final String massCancelId;

        private CancelResponse(ResponseType type, Order order, String clOrdID, byte cancelReason,
                               String rejectText, int massCancelCount, String massCancelId) {
            this.type = type;
            this.order = order;
            this.clOrdID = clOrdID;
            this.cancelReason = cancelReason;
            this.rejectText = rejectText;
            this.massCancelCount = massCancelCount;
            this.massCancelId = massCancelId;
        }

        public static CancelResponse cancelled(Order order, byte reason) {
            return new CancelResponse(ResponseType.CANCELLED, order, null, reason, null, 0, null);
        }

        public static CancelResponse rejected(String clOrdID, String text) {
            return new CancelResponse(ResponseType.REJECTED, null, clOrdID, (byte)0, text, 0, null);
        }

        public static CancelResponse massCancelled(int count, String massCancelId) {
            return new CancelResponse(ResponseType.MASS_CANCELLED, null, null, (byte)0, null, count, massCancelId);
        }

        public boolean isCancelled() { return type == ResponseType.CANCELLED; }
        public boolean isRejected() { return type == ResponseType.REJECTED; }
        public boolean isMassCancelled() { return type == ResponseType.MASS_CANCELLED; }
        public Order getOrder() { return order; }
        public String getClOrdID() { return clOrdID; }
        public byte getCancelReason() { return cancelReason; }
        public String getRejectText() { return rejectText; }
        public int getMassCancelCount() { return massCancelCount; }
        public String getMassCancelId() { return massCancelId; }

        enum ResponseType { CANCELLED, REJECTED, MASS_CANCELLED }
    }
}