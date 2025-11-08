package com.boe.simulator.server.order;

import com.boe.simulator.api.websocket.WebSocketService;
import com.boe.simulator.protocol.message.*;
import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.matching.TradeRepositoryService;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderManager {
    private static final Logger LOGGER = Logger.getLogger(OrderManager.class.getName());

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final MatchingEngine matchingEngine;

    private ClientSessionManager sessionManager;
    private WebSocketService webSocketService;

    private final ConcurrentHashMap<String, Order> activeOrdersByClOrdID;
    private final ConcurrentHashMap<Long, Order> activeOrdersByOrderID;

    private final AtomicLong orderIDGenerator;

    // Statistics
    private final AtomicLong totalOrdersReceived;
    private final AtomicLong totalOrdersAccepted;
    private final AtomicLong totalOrdersRejected;
    private final AtomicLong totalOrdersCancelled;
    private final AtomicLong totalOrdersFilled;

    public OrderManager(RocksDBManager dbManager) {
        this(new OrderRepository(dbManager), new OrderValidator(), new MatchingEngine(new OrderRepository(dbManager), new TradeRepositoryService(dbManager), false));
    }

    public OrderManager(OrderRepository orderRepository, OrderValidator orderValidator, MatchingEngine matchingEngine) {
        this.orderRepository = orderRepository;
        this.orderValidator = orderValidator;
        this.matchingEngine = matchingEngine;
        this.activeOrdersByClOrdID = new ConcurrentHashMap<>();
        this.activeOrdersByOrderID = new ConcurrentHashMap<>();
        this.orderIDGenerator = new AtomicLong(1000000);

        this.totalOrdersReceived = new AtomicLong(0);
        this.totalOrdersAccepted = new AtomicLong(0);
        this.totalOrdersRejected = new AtomicLong(0);
        this.totalOrdersCancelled = new AtomicLong(0);
        this.totalOrdersFilled = new AtomicLong(0);

        setupMatchingEngineListeners();
        loadActiveOrders();

        LOGGER.info("OrderManager initialized with MatchingEngine");
    }

    public void setSessionManager(ClientSessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    private void setupMatchingEngineListeners() {
        matchingEngine.addEventListener(new MatchingEngine.MatchingEventListener() {
            @Override
            public void onTradeExecuted(Trade trade, OrderBook book) {
                handleTradeExecution(trade);
            }

            @Override
            public void onOrderAdded(Order order, OrderBook book) {
                LOGGER.log(Level.FINE, "Order added to book: {0} @ {1}",
                        new Object[]{order.getClOrdID(), order.getPrice()});
            }

            @Override
            public void onOrderRemoved(Order order, OrderBook book) {
                LOGGER.log(Level.FINE, "Order removed from book: {0}", order.getClOrdID());
            }
        });
    }

    // ========== TCP/BOE Entry Point ==========
    public OrderResponse processNewOrder(NewOrderMessage message, ClientSession session) {
        OrderExecutionContext context = OrderExecutionContext.fromTcpSession(session);
        return processNewOrderInternal(message, context);
    }

    // ========== REST API Entry Point ==========
    public OrderResponse processNewOrder(NewOrderMessage message, String username) {
        OrderExecutionContext context = OrderExecutionContext.fromRestApi(username);
        return processNewOrderInternal(message, context);
    }

    private OrderResponse processNewOrderInternal(NewOrderMessage message, OrderExecutionContext context) {
        totalOrdersReceived.incrementAndGet();

        LOGGER.log(Level.INFO, "[{0}] Processing NewOrder: {1}",
                new Object[]{context.getSessionIdentifier(), message.getClOrdID()});

        // 1. Validate message
        OrderValidator.ValidationResult validation = orderValidator.validateNewOrder(message);
        if (!validation.isValid()) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - validation failed: {1}",
                    new Object[]{context.getSessionIdentifier(), validation.getErrorMessage()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_MISSING_REQUIRED_FIELD,
                    validation.getErrorMessage()
            );
        }

        // 2. Verify duplicate ClOrdID
        if (activeOrdersByClOrdID.containsKey(message.getClOrdID())) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - duplicate ClOrdID: {1}",
                    new Object[]{context.getSessionIdentifier(), message.getClOrdID()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_DUPLICATE_CLORDID,
                    "Duplicate ClOrdID: " + message.getClOrdID()
            );
        }

        // 3. Create order
        try {
            long orderID = orderIDGenerator.getAndIncrement();

            Order order = Order.builder()
                    .clOrdID(message.getClOrdID())
                    .orderID(orderID)
                    .sessionSubID(context.getSessionIdentifier()) // "TCP-123" o "REST-API"
                    .username(context.getUsername())
                    .side(message.getSide())
                    .orderQty(message.getOrderQty())
                    .price(message.getPrice())
                    .ordType(message.getOrdType() != 0 ? message.getOrdType() : (byte)2)
                    .symbol(message.getSymbol())
                    .capacity(message.getCapacity())
                    .account(message.getAccount() != null ? message.getAccount() : "")
                    .clearingFirm(message.getClearingFirm() != null ? message.getClearingFirm() : "")
                    .routingInst(message.getRoutingInst() != 0 ? message.getRoutingInst() : (byte)'B')
                    .receivedSequence(message.getSequenceNumber())
                    .matchingUnit(message.getMatchingUnit())
                    .build();

            // 4. Acknowledge order
            order.acknowledge();

            // 5. Add to cache
            activeOrdersByClOrdID.put(order.getClOrdID(), order);
            activeOrdersByOrderID.put(order.getOrderID(), order);

            // 6. Send to matching engine
            List<Trade> trades = matchingEngine.processOrder(order);

            // 7. If no trades, save to DB (if trades, execution handler saves)
            if (trades.isEmpty()) {
                orderRepository.save(order);
            }

            totalOrdersAccepted.incrementAndGet();

            LOGGER.log(Level.INFO, "[{0}] Order accepted: {1} (OrderID: {2}, Trades: {3})",
                    new Object[]{
                            context.getSessionIdentifier(),
                            order.getClOrdID(),
                            order.getOrderID(),
                            trades.size()
                    });

            return OrderResponse.acknowledged(order);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + context.getSessionIdentifier() + "] Error processing order", e);
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_UNKNOWN_ERROR,
                    "Internal error: " + e.getMessage()
            );
        }
    }

    // ========== TCP/BOE Cancel ==========
    public CancelResponse processCancelOrder(CancelOrderMessage message, ClientSession session) {
        OrderExecutionContext context = OrderExecutionContext.fromTcpSession(session);
        return processCancelOrderInternal(message, context);
    }

    // ========== REST API Cancel ==========
    public CancelResponse processCancelOrder(String clOrdID, String username) {
        OrderExecutionContext context = OrderExecutionContext.fromRestApi(username);
        CancelOrderMessage message = new CancelOrderMessage(clOrdID);
        return processCancelOrderInternal(message, context);
    }

    private CancelResponse processCancelOrderInternal(CancelOrderMessage message, OrderExecutionContext context) {
        LOGGER.log(Level.INFO, "[{0}] Processing CancelOrder: {1}",
                new Object[]{context.getSessionIdentifier(), message});

        if (message.isMassCancel()) return processMassCancel(message, context);

        return processSingleCancel(message.getOrigClOrdID(), context);
    }

    private CancelResponse processSingleCancel(String origClOrdID, OrderExecutionContext context) {
        Order order = activeOrdersByClOrdID.get(origClOrdID);

        if (order == null) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - order not found: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});
            return CancelResponse.rejected(origClOrdID, "Order not found or already terminated");
        }

        // Check permissions
        if (!order.getUsername().equals(context.getUsername())) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - unauthorized: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});
            return CancelResponse.rejected(origClOrdID, "Unauthorized: order belongs to different user");
        }

        // Check state
        if (!order.getState().isCancellable()) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - not cancellable: {1} (state: {2})",
                    new Object[]{context.getSessionIdentifier(), origClOrdID, order.getState()});
            return CancelResponse.rejected(origClOrdID, "Order not cancellable in state: " + order.getState());
        }

        // Cancel order
        try {
            matchingEngine.cancelOrder(order);

            order.cancel();
            orderRepository.save(order);

            activeOrdersByClOrdID.remove(order.getClOrdID());
            activeOrdersByOrderID.remove(order.getOrderID());

            totalOrdersCancelled.incrementAndGet();

            LOGGER.log(Level.INFO, "[{0}] Order cancelled: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});

            return CancelResponse.cancelled(order, OrderCancelledMessage.REASON_USER_REQUESTED);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + context.getSessionIdentifier() + "] Error cancelling order", e);
            return CancelResponse.rejected(origClOrdID, "Internal error: " + e.getMessage());
        }
    }

    private CancelResponse processMassCancel(CancelOrderMessage message, OrderExecutionContext context) {
        LOGGER.log(Level.INFO, "[{0}] Processing Mass Cancel: type={1}",
                new Object[]{context.getSessionIdentifier(), message.getMassCancelType()});

        List<Order> ordersToCancel = switch (message.getMassCancelType()) {
            case FIRM -> filterOrdersByClearingFirm(message.getClearingFirm(), context);
            case SYMBOL -> filterOrdersBySymbol(message.getRiskRoot(), context);
            case ALL -> filterAllOrders(context);
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

        LOGGER.log(Level.INFO, "[{0}] Mass Cancel completed: {1} orders cancelled",
                new Object[]{context.getSessionIdentifier(), cancelledCount});

        return CancelResponse.massCancelled(cancelledCount, message.getMassCancelId());
    }

    private List<Order> filterOrdersByClearingFirm(String clearingFirm, OrderExecutionContext context) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(context.getUsername()))
                .filter(o -> clearingFirm.equals(o.getClearingFirm()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private List<Order> filterOrdersBySymbol(String symbol, OrderExecutionContext context) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(context.getUsername()))
                .filter(o -> symbol.equals(o.getSymbol()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private List<Order> filterAllOrders(OrderExecutionContext context) {
        return activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(context.getUsername()))
                .filter(o -> o.getState().isCancellable())
                .toList();
    }

    private void handleTradeExecution(Trade trade) {
        LOGGER.log(Level.INFO, "Trade executed: {0}", trade);

        Order buyOrder = activeOrdersByOrderID.get(trade.getBuyOrderId());
        Order sellOrder = activeOrdersByOrderID.get(trade.getSellOrderId());

        if (buyOrder != null && buyOrder.isFilled()) {
            totalOrdersFilled.incrementAndGet();
            activeOrdersByClOrdID.remove(buyOrder.getClOrdID());
            activeOrdersByOrderID.remove(buyOrder.getOrderID());
        }

        if (sellOrder != null && sellOrder.isFilled()) {
            totalOrdersFilled.incrementAndGet();
            activeOrdersByClOrdID.remove(sellOrder.getClOrdID());
            activeOrdersByOrderID.remove(sellOrder.getOrderID());
        }

        if (sessionManager != null) sendExecutionMessages(trade, buyOrder, sellOrder);

        if (webSocketService != null) {
            webSocketService.broadcastTrade(trade);
            // Notify order status updates
            if (buyOrder != null) webSocketService.broadcastOrderStatus(buyOrder);
            if (sellOrder != null) webSocketService.broadcastOrderStatus(sellOrder);
        }
    }

    private void sendExecutionMessages(Trade trade, Order buyOrder, Order sellOrder) {
        if (buyOrder != null) sendExecutionMessage(buyOrder, trade, true);
        if (sellOrder != null) sendExecutionMessage(sellOrder, trade, false);
    }

    private void sendExecutionMessage(Order order, Trade trade, boolean isAggressive) {
        ClientConnectionHandler handler = sessionManager.getHandlerByUsername(order.getUsername());

        if (handler != null && handler.getSession().isAuthenticated()) {
            try {
                OrderExecutedMessage execMsg = OrderExecutedMessage.fromTrade(trade, order, isAggressive);
                execMsg.setMatchingUnit(handler.getSession().getMatchingUnit());
                execMsg.setSequenceNumber(handler.getSession().getNextSentSequenceNumber());

                byte[] msgBytes = execMsg.toBytes();
                handler.sendMessage(msgBytes);

                LOGGER.log(Level.INFO, "Sent execution to {0}: {1}",
                        new Object[]{order.getUsername(), execMsg});

            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Failed to send execution message to " + order.getUsername(), e);
            }
        } else LOGGER.log(Level.FINE, "User not connected via TCP: {0} (order from REST API)", order.getUsername());

    }

    private void loadActiveOrders() {
        try {
            List<Order> activeOrders = orderRepository.findActiveOrders();
            for (Order order : activeOrders) {
                activeOrdersByClOrdID.put(order.getClOrdID(), order);
                activeOrdersByOrderID.put(order.getOrderID(), order);

                matchingEngine.getOrderBook(order.getSymbol()).ifPresent(book -> book.addOrder(order));
            }
            LOGGER.log(Level.INFO, "Loaded {0} active orders from database", activeOrders.size());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load active orders", e);
        }
    }

    // Getters
    public long getTotalOrdersReceived() { return totalOrdersReceived.get(); }
    public long getTotalOrdersAccepted() { return totalOrdersAccepted.get(); }
    public long getTotalOrdersRejected() { return totalOrdersRejected.get(); }
    public long getTotalOrdersCancelled() { return totalOrdersCancelled.get(); }
    public long getTotalOrdersFilled() { return totalOrdersFilled.get(); }
    public MatchingEngine getMatchingEngine() {return matchingEngine; }
    public int getActiveOrderCount() { return activeOrdersByClOrdID.size(); }

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
        LOGGER.log(Level.INFO, "Total Filled: {0}", totalOrdersFilled.get());
        LOGGER.log(Level.INFO, "Active Orders: {0}", activeOrdersByClOrdID.size());
        LOGGER.info("======================================");

        matchingEngine.printStatistics();
    }

    public OrderRepository getOrderRepository() {
        return orderRepository;
    }

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

        public boolean isAcknowledged() {
            return type == ResponseType.ACKNOWLEDGED;
        }

        public boolean isRejected() {
            return type == ResponseType.REJECTED;
        }

        public Order getOrder() {
            return order;
        }

        public String getClOrdID() {
            return clOrdID;
        }

        public byte getRejectReason() {
            return rejectReason;
        }

        public String getRejectText() {
            return rejectText;
        }

        enum ResponseType {
            ACKNOWLEDGED,
            REJECTED
        }
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

        public boolean isCancelled() {
            return type == ResponseType.CANCELLED;
        }

        public boolean isRejected() {
            return type == ResponseType.REJECTED;
        }

        public boolean isMassCancelled() {
            return type == ResponseType.MASS_CANCELLED;
        }

        public Order getOrder() {
            return order;
        }

        public String getClOrdID() {
            return clOrdID;
        }

        public byte getCancelReason() {
            return cancelReason;
        }

        public String getRejectText() {
            return rejectText;
        }

        public int getMassCancelCount() {
            return massCancelCount;
        }

        public String getMassCancelId() {
            return massCancelId;
        }

        enum ResponseType {
            CANCELLED,
            REJECTED,
            MASS_CANCELLED
        }
    }
}