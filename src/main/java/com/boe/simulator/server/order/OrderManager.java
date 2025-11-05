package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.*;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.session.ClientSession;

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
    private final MatchingEngine matchingEngine;

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
        this.matchingEngine = new MatchingEngine();
        this.activeOrdersByClOrdID = new ConcurrentHashMap<>();
        this.activeOrdersByOrderID = new ConcurrentHashMap<>();
        this.orderIDGenerator = new AtomicLong(1000000);

        this.totalOrdersReceived = new AtomicLong(0);
        this.totalOrdersAccepted = new AtomicLong(0);
        this.totalOrdersRejected = new AtomicLong(0);
        this.totalOrdersCancelled = new AtomicLong(0);

        setupTradeListener();

        loadActiveOrders();

        LOGGER.info("OrderManager initialized with Matching Engine");
    }

    private void setupTradeListener() {
        matchingEngine.addTradeListener(trade -> {
            LOGGER.log(Level.INFO, "Trade executed: {0}", trade);
        });
    }

    public OrderResponse processNewOrder(NewOrderMessage message, ClientSession session) {
        totalOrdersReceived.incrementAndGet();

        LOGGER.log(Level.INFO, "[Session {0}] Processing NewOrder: {1}", new Object[]{
                session.getConnectionId(),
                message.getClOrdID()
        });

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
                    .ordType(message.getOrdType() != 0 ? message.getOrdType() : (byte)2)
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

            order.acknowledge();

            MatchingEngine.MatchResult matchResult = matchingEngine.processOrder(order);

            orderRepository.save(order);
            if (!order.isFilled()) {
                activeOrdersByClOrdID.put(order.getClOrdID(), order);
                activeOrdersByOrderID.put(order.getOrderID(), order);
            }

            totalOrdersAccepted.incrementAndGet();

            LOGGER.log(Level.INFO, "[Session {0}] Order processed: {1} (OrderID: {2}), Match result: {3} trades, status: {4}",
                    new Object[]{
                            session.getConnectionId(),
                            order.getClOrdID(),
                            order.getOrderID(),
                            matchResult.getTrades().size(),
                            matchResult.getOrderStatus()
                    });

            return OrderResponse.acknowledged(order, matchResult);

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
        LOGGER.log(Level.INFO, "[Session {0}] Processing CancelOrder", session.getConnectionId());

        // Verificar si es mass cancel
        if (message.isMassCancel()) return processMassCancel(message, session);

        // Single order cancel
        return processSingleCancel(message.getOrigClOrdID(), session);
    }

    private CancelResponse processSingleCancel(String origClOrdID, ClientSession session) {
        Order order = activeOrdersByClOrdID.get(origClOrdID);

        if (order == null) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - order not found: {1}", new Object[]{
                    session.getConnectionId(),
                    origClOrdID
            });
            return CancelResponse.rejected(
                    origClOrdID,
                    "Order not found or already terminated"
            );
        }

        if (!order.getUsername().equals(session.getUsername())) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - unauthorized: {1}",
                    new Object[]{session.getConnectionId(), origClOrdID});
            return CancelResponse.rejected(
                    origClOrdID,
                    "Unauthorized: order belongs to different user"
            );
        }

        // Verificar estado
        if (!order.getState().isCancellable()) {
            LOGGER.log(Level.WARNING, "[Session {0}] Cancel rejected - not cancellable: {1} (state: {2})", new Object[]{
                    session.getConnectionId(),
                    origClOrdID,
                    order.getState()
            });
            return CancelResponse.rejected(
                    origClOrdID,
                    "Order not cancellable in state: " + order.getState()
            );
        }

        try {
            matchingEngine.cancelOrder(order);

            order.cancel();
            orderRepository.save(order);

            // Remover del cache
            activeOrdersByClOrdID.remove(order.getClOrdID());
            activeOrdersByOrderID.remove(order.getOrderID());

            totalOrdersCancelled.incrementAndGet();

            LOGGER.log(Level.INFO, "[Session {0}] Order cancelled: {1}", new Object[]{
                    session.getConnectionId(),
                    origClOrdID
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
        LOGGER.log(Level.INFO, "[Session {0}] Processing Mass Cancel: type={1}", new Object[]{
                session.getConnectionId(),
                message.getMassCancelType()
        });

        List<Order> ordersToCancel = switch (message.getMassCancelType()) {
            case FIRM -> filterOrdersByClearingFirm(message.getClearingFirm(), session);
            case SYMBOL -> filterOrdersBySymbol(message.getRiskRoot(), session);
            case ALL -> filterAllOrders(session);
            default -> List.of();
        };

        int cancelledCount = 0;

        for (Order order : ordersToCancel) {
            try {
                matchingEngine.cancelOrder(order);
                order.cancel();
                orderRepository.save(order);

                activeOrdersByClOrdID.remove(order.getClOrdID());
                activeOrdersByOrderID.remove(order.getOrderID());

                cancelledCount++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cancel order: " + order.getClOrdID(), e);
            }
        }

        totalOrdersCancelled.addAndGet(cancelledCount);

        LOGGER.log(Level.INFO, "[Session {0}] Mass Cancel completed: {1} orders cancelled", new Object[]{
                session.getConnectionId(),
                cancelledCount
        });

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

                matchingEngine.processOrder(order);
            }
            LOGGER.log(Level.INFO, "Loaded {0} active orders from database", activeOrders.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load active orders", e);
        }
    }

    public Optional<MatchingEngine.OrderBookSnapshot> getOrderBook(String symbol) {
        return matchingEngine.getOrderBook(symbol);
    }

    // Getters para statistics
    public long getTotalOrdersReceived() { return totalOrdersReceived.get(); }

    public long getTotalOrdersAccepted() { return totalOrdersAccepted.get(); }

    public long getTotalOrdersRejected() { return totalOrdersRejected.get(); }

    public long getTotalOrdersCancelled() { return totalOrdersCancelled.get(); }

    public int getActiveOrderCount() { return activeOrdersByClOrdID.size(); }

    public long getTotalTrades() { return matchingEngine.getTotalTrades(); }

    public long getTotalVolume() { return matchingEngine.getTotalVolume(); }

    public Optional<Order> findByClOrdID(String clOrdID) {
        Order order = activeOrdersByClOrdID.get(clOrdID);
        if (order != null) return Optional.of(order);

        return orderRepository.findByClOrdID(clOrdID);
    }

    public void printStatistics() {
        LOGGER.info("========== Order Statistics ==========");
        LOGGER.log(Level.INFO, "Total Received: {0}", totalOrdersReceived.get());
        LOGGER.log(Level.INFO, "Total Accepted: {0}", totalOrdersAccepted.get());
        LOGGER.log(Level.INFO, "Total Rejected: {0}", totalOrdersRejected.get());
        LOGGER.log(Level.INFO, "Total Cancelled: {0}", totalOrdersCancelled.get());
        LOGGER.log(Level.INFO, "Active Orders: {0}", activeOrdersByClOrdID.size());
        LOGGER.log(Level.INFO, "Total Trades: {0}", matchingEngine.getTotalTrades());
        LOGGER.log(Level.INFO, "Total Volume: {0}", matchingEngine.getTotalVolume());
        LOGGER.log(Level.INFO, "Order Books: {0}", matchingEngine.getOrderBookCount());
        LOGGER.info("=====================================");
    }

    public static class OrderResponse {
        private final ResponseType type;
        private final Order order;
        private final String clOrdID;
        private final byte rejectReason;
        private final String rejectText;
        private final MatchingEngine.MatchResult matchResult;  // NUEVO

        private OrderResponse(ResponseType type, Order order, String clOrdID, byte rejectReason,
                              String rejectText, MatchingEngine.MatchResult matchResult) {
            this.type = type;
            this.order = order;
            this.clOrdID = clOrdID;
            this.rejectReason = rejectReason;
            this.rejectText = rejectText;
            this.matchResult = matchResult;
        }

        public static OrderResponse acknowledged(Order order, MatchingEngine.MatchResult matchResult) {
            return new OrderResponse(ResponseType.ACKNOWLEDGED, order, null, (byte)0, null, matchResult);
        }

        public static OrderResponse rejected(String clOrdID, byte reason, String text) {
            return new OrderResponse(ResponseType.REJECTED, null, clOrdID, reason, text, null);
        }

        public boolean isAcknowledged() { return type == ResponseType.ACKNOWLEDGED; }

        public boolean isRejected() { return type == ResponseType.REJECTED; }

        public Order getOrder() { return order; }

        public String getClOrdID() { return clOrdID; }

        public byte getRejectReason() { return rejectReason; }

        public String getRejectText() { return rejectText; }

        public MatchingEngine.MatchResult getMatchResult() { return matchResult; }

        public boolean hasExecutions() { return matchResult != null && matchResult.hasExecutions(); }

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

        private CancelResponse(ResponseType type, Order order, String clOrdID, byte cancelReason, String rejectText, int massCancelCount, String massCancelId) {
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