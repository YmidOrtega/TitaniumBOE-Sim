package com.boe.simulator.server.order;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.IntFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.api.websocket.WebSocketService;
import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.CancelRejectedMessage;
import com.boe.simulator.protocol.message.ModifyOrderMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import java.math.BigDecimal;
import com.boe.simulator.protocol.types.Capacity;
import com.boe.simulator.protocol.types.OpenClose;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.PutOrCall;
import com.boe.simulator.protocol.types.RoutingInst;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.protocol.types.TimeInForce;
import com.boe.simulator.protocol.message.OrderCancelledMessage;
import com.boe.simulator.protocol.message.OrderExecutedMessage;
import com.boe.simulator.protocol.message.OrderRejectedMessage;
import com.boe.simulator.protocol.message.ReturnBitfields;
import com.boe.simulator.protocol.message.UserModifyRejectedMessage;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.matching.TradeRepositoryService;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.session.BoeSessionState;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

public class OrderManager {
    private static final Logger LOGGER = Logger.getLogger(OrderManager.class.getName());

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final MatchingEngine matchingEngine;

    private ClientSessionManager sessionManager;
    private WebSocketService webSocketService;
    private int maxOpenOrdersPerSession = ServerConfiguration.getDefault().getMaxOpenOrdersPerSession();

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

    public void setMaxOpenOrdersPerSession(int maxOpenOrdersPerSession) {
        this.maxOpenOrdersPerSession = maxOpenOrdersPerSession;
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

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "[{0}] Processing NewOrder: {1}",
                    new Object[]{context.getSessionIdentifier(), message.getClOrdID()});
        }

        // 1. Optional fields the simulator cannot honor (Input Bitfields Per Message)
        if (message.getFieldError() != null) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - {1}", new Object[]{context.getSessionIdentifier(), message.getFieldError()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(message.getClOrdID(), OrderRejectedMessage.REASON_UNFORESEEN, message.getFieldError());
        }

        String timeInForceError = timeInForceError(message.getTimeInForce());
        if (timeInForceError != null) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - {1}", new Object[]{context.getSessionIdentifier(), timeInForceError});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(message.getClOrdID(), OrderRejectedMessage.REASON_UNFORESEEN, timeInForceError);
        }

        // 2. Validate message
        OrderValidator.ValidationResult validation = orderValidator.validateNewOrder(message);
        if (!validation.isValid()) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - validation failed: {1}",
                    new Object[]{context.getSessionIdentifier(), validation.errorMessage()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_MISSING_REQUIRED_FIELD,
                    validation.errorMessage()
            );
        }
        
        // 3. Validate symbol
        if (!isValidSymbol(message.getSymbol())) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "[{0}] Order rejected - invalid symbol: {1}",
                        new Object[]{context.getSessionIdentifier(), message.getSymbol()});
            }
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_INVALID_SYMBOL,
                    "Invalid or unknown symbol: " + message.getSymbol()
            );
        }

        // 4. Verify duplicate ClOrdID
        if (activeOrdersByClOrdID.containsKey(message.getClOrdID())) {
            if (LOGGER.isLoggable(Level.WARNING)) {
                LOGGER.log(Level.WARNING, "[{0}] Order rejected - duplicate ClOrdID: {1}",
                        new Object[]{context.getSessionIdentifier(), message.getClOrdID()});
            }
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_DUPLICATE_CLORDID,
                    "Duplicate ClOrdID: " + message.getClOrdID()
            );
        }

        // 5. Max open orders per BOE port
        if (context instanceof TcpExecutionContext
                && countOpenBoeOrders(context.getUsername()) >= maxOpenOrdersPerSession) {
            LOGGER.log(Level.WARNING, "[{0}] Order rejected - max open orders ({1}) reached: {2}",
                    new Object[]{context.getSessionIdentifier(), maxOpenOrdersPerSession, message.getClOrdID()});
            totalOrdersRejected.incrementAndGet();
            return OrderResponse.rejected(
                    message.getClOrdID(),
                    OrderRejectedMessage.REASON_MAX_OPEN_ORDERS_EXCEEDED,
                    "Max open orders count exceeded (" + maxOpenOrdersPerSession + ")"
            );
        }

        // 6. Create order
        try {
            long orderID = orderIDGenerator.getAndIncrement();

            Order order = Order.builder()
                    .clOrdID(message.getClOrdID())
                    .orderID(orderID)
                    .sessionSubID(context.getSessionIdentifier()) // "TCP-123" o "REST-API"
                    .username(context.getUsername())
                    .side(Side.fromByte(message.getSide()))
                    .orderQty(message.getOrderQty())
                    .price(message.getPrice())
                    .ordType(message.getOrdType() != 0 ? OrdType.fromByte(message.getOrdType()) : OrdType.LIMIT)
                    .timeInForce(TimeInForce.fromByte(message.getTimeInForce()))
                    .symbol(message.getSymbol())
                    .capacity(message.getCapacity() != 0 ? Capacity.fromByte(message.getCapacity()) : Capacity.AGENCY)
                    .openClose(message.getOpenClose() != 0 ? OpenClose.fromByte(message.getOpenClose()) : OpenClose.NONE)
                    .putOrCall(message.getPutOrCall() != 0 ? PutOrCall.fromByte(message.getPutOrCall()) : null)
                    .account(message.getAccount() != null ? message.getAccount() : "")
                    .clearingFirm(message.getClearingFirm() != null ? message.getClearingFirm() : "")
                    .routingInst(message.getRoutingInst() != 0 ? RoutingInst.fromByte(message.getRoutingInst()) : RoutingInst.BOOK_ONLY)
                    .receivedSequence(message.getSequenceNumber())
                    .matchingUnit(BoeSessionState.MATCHING_UNIT)
                    .build();

            // 7. Acknowledge order
            order.acknowledge();

            // 8. Add to cache
            activeOrdersByClOrdID.put(order.getClOrdID(), order);
            activeOrdersByOrderID.put(order.getOrderID(), order);

            // 9. Send to matching engine
            List<Trade> trades = matchingEngine.processOrder(order);

            if (order.getState() == OrderState.CANCELLED) {
                activeOrdersByClOrdID.remove(order.getClOrdID());
                activeOrdersByOrderID.remove(order.getOrderID());
                totalOrdersCancelled.incrementAndGet();
            }

            // 10. Enqueue for async persistence — keeps disk I/O off the NewOrder → ACK hot path
            orderRepository.saveAsync(order);

            totalOrdersAccepted.incrementAndGet();

            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE, "[{0}] Order accepted: {1} (OrderID: {2}, Trades: {3})",
                        new Object[]{
                                context.getSessionIdentifier(),
                                order.getClOrdID(),
                                order.getOrderID(),
                                trades.size()
                        });
            }

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

    // ========== TCP/BOE Modify ==========
    public ModifyResponse processModifyOrder(ModifyOrderMessage message, ClientSession session) {
        OrderExecutionContext context = OrderExecutionContext.fromTcpSession(session);

        LOGGER.log(Level.INFO, "[{0}] Processing ModifyOrder: origClOrdID={1}, newClOrdID={2}",
                new Object[]{context.getSessionIdentifier(),
                        message.getOrigClOrdID(), message.getClOrdID()});

        Order order = activeOrdersByClOrdID.get(message.getOrigClOrdID());
        if (order == null || !order.getUsername().equals(context.getUsername())) {
            return ModifyResponse.rejected(message.getClOrdID(),
                    UserModifyRejectedMessage.REASON_NOT_FOUND,
                    "Order not found or already terminated");
        }
        if (!order.getState().isActive()) {
            return ModifyResponse.rejected(message.getClOrdID(),
                    UserModifyRejectedMessage.REASON_TOO_LATE_TO_CANCEL,
                    "Order not modifiable in state: " + order.getState());
        }

        String invalid = validateModify(message, order);
        if (invalid != null) return rejectModify(message, order, UserModifyRejectedMessage.REASON_UNFORESEEN, invalid, context);

        BigDecimal newPrice   = message.getPrice() != null ? message.getPrice() : order.getPrice();
        OrdType    newOrdType = message.getOrdType() != 0 ? OrdType.fromByte(message.getOrdType()) : null;
        int        newOrderQty = message.getOrderQty();

        String newClOrdID = message.getClOrdID();
        if (newClOrdID.equals(message.getOrigClOrdID())) {
            if (!reducesQuantityOnly(order, newPrice, newOrdType, newOrderQty)) {
                return rejectModify(message, order, UserModifyRejectedMessage.REASON_DUPLICATE_CLORDID,
                        "ClOrdID can only be reused when the Modify only reduces OrderQty", context);
            }
        } else if (activeOrdersByClOrdID.containsKey(newClOrdID)) {
            return rejectModify(message, order, UserModifyRejectedMessage.REASON_DUPLICATE_CLORDID,
                    "Duplicate ClOrdID: " + newClOrdID, context);
        }

        String currentClOrdID = order.getClOrdID();
        activeOrdersByClOrdID.remove(currentClOrdID);

        try {
            matchingEngine.modifyOrder(order, newClOrdID, newPrice, newOrdType, newOrderQty);

            if (order.getState().isActive()) {
                activeOrdersByClOrdID.put(order.getClOrdID(), order);
                orderRepository.saveAsync(order);
                LOGGER.log(Level.INFO, "[{0}] Order modified: {1} (OrderID: {2})",
                        new Object[]{context.getSessionIdentifier(),
                                order.getClOrdID(), order.getOrderID()});
                return ModifyResponse.modified(order);
            } else {
                activeOrdersByOrderID.remove(order.getOrderID());
                orderRepository.saveAsync(order);
                totalOrdersCancelled.incrementAndGet();
                LOGGER.log(Level.INFO, "[{0}] Order auto-cancelled by modify: {1}",
                        new Object[]{context.getSessionIdentifier(), order.getClOrdID()});
                return ModifyResponse.autoCancelled(order);
            }

        } catch (Exception e) {
            activeOrdersByClOrdID.put(currentClOrdID, order);
            LOGGER.log(Level.SEVERE, "[" + context.getSessionIdentifier() + "] Error modifying order", e);
            return ModifyResponse.rejected(message.getClOrdID(),
                    UserModifyRejectedMessage.REASON_UNFORESEEN,
                    "Internal error: " + e.getMessage());
        }
    }

    // Modify Order rules (p.77); null = valid
    private static String validateModify(ModifyOrderMessage message, Order order) {
        if (message.getFieldError() != null) return message.getFieldError();
        if (message.getClOrdID().isEmpty()) return "ClOrdID is required in Modify Order";
        if (!message.hasOrderQty()) return "OrderQty is required in Modify Order";
        if (message.getOrderQty() < 0 || message.getOrderQty() > 999_999) return "OrderQty must be between 0 and 999,999";

        byte ordType = message.getOrdType();
        if (ordType == '3' || ordType == '4') return "Stop and Stop Limit orders are not supported by the simulator";
        if (ordType != 0 && ordType != '1' && ordType != '2') return "Invalid OrdType: 0x" + Integer.toHexString(ordType & 0xFF);

        boolean isMarket = ordType == '1' || (ordType == 0 && order.getOrdType() == OrdType.MARKET);
        if (!isMarket) {
            if (!message.hasPrice() || message.getPrice() == null) return "Price is required in Modify Order for limit orders";
            if (message.getPrice().signum() < 0) return "Price cannot be negative";
        }

        if (order.getModifyCount() >= MAX_MODIFICATIONS_PER_ORDER)
            return "Maximum of 1,295 modifications reached; the order can only be cancelled";
        return null;
    }

    // Time priority is kept, and the ClOrdID may be reused, only when OrderQty decreases with no other change (p.77)
    private static boolean reducesQuantityOnly(Order order, BigDecimal newPrice, OrdType newOrdType, int newOrderQty) {
        boolean samePrice = newPrice == null || order.getPrice() == null || newPrice.compareTo(order.getPrice()) == 0;
        boolean sameOrdType = newOrdType == null || newOrdType == order.getOrdType();
        return samePrice && sameOrdType && newOrderQty < order.getEffectiveOrderQty();
    }

    private ModifyResponse rejectModify(ModifyOrderMessage message, Order order, byte reason, String text,
                                        OrderExecutionContext context) {
        LOGGER.log(Level.WARNING, "[{0}] Modify rejected for {1}: {2}",
                new Object[]{context.getSessionIdentifier(), message.getOrigClOrdID(), text});
        if (!message.cancelsOrigOnReject()) return ModifyResponse.rejected(message.getClOrdID(), reason, text);

        CancelResponse cancel = processSingleCancel(order.getClOrdID(), context);
        return cancel.isCancelled()
                ? ModifyResponse.rejectedAndCancelled(message.getClOrdID(), reason, text, cancel.getOrder())
                : ModifyResponse.rejected(message.getClOrdID(), reason, text);
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

        if (message.getFieldError() != null) {
            return CancelResponse.rejected(message.getOrigClOrdID(), CancelRejectedMessage.REASON_UNFORESEEN, message.getFieldError());
        }
        if (message.isMassCancel()) return processMassCancel(message, context);

        return processSingleCancel(message.getOrigClOrdID(), context);
    }

    private CancelResponse processSingleCancel(String origClOrdID, OrderExecutionContext context) {
        Order order = activeOrdersByClOrdID.get(origClOrdID);

        if (order == null) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - order not found: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});
            return CancelResponse.rejected(origClOrdID, CancelRejectedMessage.REASON_ORDER_NOT_FOUND, "Order not found or already terminated");
        }

        // Check permissions
        if (!order.getUsername().equals(context.getUsername())) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - unauthorized: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});
            return CancelResponse.rejected(origClOrdID, CancelRejectedMessage.REASON_ORDER_NOT_FOUND, "Order not found or already terminated");
        }

        // Check state
        if (!order.getState().isCancellable()) {
            LOGGER.log(Level.WARNING, "[{0}] Cancel rejected - not cancellable: {1} (state: {2})",
                    new Object[]{context.getSessionIdentifier(), origClOrdID, order.getState()});
            return CancelResponse.rejected(origClOrdID, CancelRejectedMessage.REASON_TOO_LATE_TO_CANCEL, "Order not cancellable in state: " + order.getState());
        }

        // Cancel order
        try {
            matchingEngine.cancelOrder(order);

            order.cancel();
            orderRepository.saveAsync(order);

            activeOrdersByClOrdID.remove(order.getClOrdID());
            activeOrdersByOrderID.remove(order.getOrderID());

            totalOrdersCancelled.incrementAndGet();

            LOGGER.log(Level.INFO, "[{0}] Order cancelled: {1}",
                    new Object[]{context.getSessionIdentifier(), origClOrdID});

            return CancelResponse.cancelled(order, OrderCancelledMessage.REASON_USER_REQUESTED);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "[" + context.getSessionIdentifier() + "] Error cancelling order", e);
            return CancelResponse.rejected(origClOrdID, CancelRejectedMessage.REASON_UNFORESEEN, "Internal error: " + e.getMessage());
        }
    }

    private static String timeInForceError(byte value) {
        TimeInForce timeInForce;
        try {
            timeInForce = TimeInForce.fromByte(value);
        } catch (IllegalArgumentException e) {
            return "Invalid TimeInForce '" + (char) value + "'";
        }
        return switch (timeInForce) {
            case DAY, IOC, FOK -> null;
            default -> "TimeInForce " + timeInForce + " is not supported by the simulator";
        };
    }

    private long countOpenBoeOrders(String username) {
        return activeOrdersByOrderID.values().stream()
                .filter(o -> o.getState().isActive())
                .filter(o -> o.getUsername() != null && o.getUsername().equals(username))
                .filter(o -> o.getSessionSubID() != null && o.getSessionSubID().startsWith(TcpExecutionContext.SESSION_PREFIX))
                .count();
    }

    private CancelResponse processMassCancel(CancelOrderMessage message, OrderExecutionContext context) {
        LOGGER.log(Level.INFO, "[{0}] Processing Mass Cancel: {1}", new Object[]{context.getSessionIdentifier(), message});

        String invalid = validateMassCancel(message);
        if (invalid != null) {
            LOGGER.log(Level.WARNING, "[{0}] Mass Cancel rejected: {1}", new Object[]{context.getSessionIdentifier(), invalid});
            return CancelResponse.rejected(message.getOrigClOrdID(), CancelRejectedMessage.REASON_UNFORESEEN, invalid);
        }

        String clearingFirm = message.massCancelInstChar(1) == 'F' && !message.getClearingFirm().isBlank()
                ? message.getClearingFirm() : null;
        String riskRoot = message.getRiskRoot() != null && !message.getRiskRoot().isBlank() ? message.getRiskRoot() : null;
        boolean complexOnly = Character.valueOf('C').equals(message.massCancelInstChar(4));

        List<Order> ordersToCancel = complexOnly ? List.of() : activeOrdersByClOrdID.values().stream()
                .filter(o -> o.getUsername().equals(context.getUsername()))
                .filter(o -> clearingFirm == null || clearingFirm.equals(o.getClearingFirm()))
                .filter(o -> riskRoot == null || riskRoot.equals(o.getSymbol()))
                .filter(o -> o.getState().isCancellable())
                .toList();

        List<Order> cancelled = new ArrayList<>();
        for (Order order : ordersToCancel) {
            try {
                matchingEngine.cancelOrder(order);
                order.cancel();
                orderRepository.saveAsync(order);

                activeOrdersByClOrdID.remove(order.getClOrdID());
                activeOrdersByOrderID.remove(order.getOrderID());

                cancelled.add(order);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to cancel order: " + order.getClOrdID(), e);
            }
        }

        totalOrdersCancelled.addAndGet(cancelled.size());

        LOGGER.log(Level.INFO, "[{0}] Mass Cancel completed: {1} orders cancelled",
                new Object[]{context.getSessionIdentifier(), cancelled.size()});

        Character style = message.massCancelInstChar(2);
        return CancelResponse.massCancelled(cancelled, style != null ? style : 'M', message.getMassCancelId());
    }

    // MassCancelInst rules from List of Optional Fields (p.204); null = valid
    private static String validateMassCancel(CancelOrderMessage message) {
        String inst = message.getMassCancelInst();
        if (!message.hasMassCancelInst() || inst == null || inst.isEmpty()) return "MassCancelInst is required for a mass cancel";

        char firmFilter = message.massCancelInstChar(1);
        if (firmFilter != 'A' && firmFilter != 'F') return "Invalid Clearing Firm Filter '" + firmFilter + "' in MassCancelInst";
        if (firmFilter == 'F' && !message.hasClearingFirm()) return "ClearingFirm is required with Clearing Firm Filter F";

        Character ackStyle = message.massCancelInstChar(2);
        char style = ackStyle != null ? ackStyle : 'M';
        if (style == 'A' || style == 'I') return "Acknowledgement Style " + style + " is only valid on Purge Orders";
        if (style != 'M' && style != 'S' && style != 'B') return "Invalid Acknowledgement Style '" + style + "' in MassCancelInst";

        String id = message.getMassCancelId();
        boolean hasId = id != null && !id.isEmpty();
        if ((style == 'S' || style == 'B') && !hasId) return "MassCancelID is required with Acknowledgement Style " + style;
        if (style == 'M' && hasId) return "MassCancelID must be blank with Acknowledgement Style M";
        if (hasId && id.endsWith(" ")) return "MassCancelID must not end in a space";

        Character lockout = message.massCancelInstChar(3);
        if (lockout != null && lockout != 'N' && lockout != 'L') return "Invalid Lockout Instruction '" + lockout + "' in MassCancelInst";
        if (lockout != null && lockout == 'L') return "Lockout is not supported by the simulator";

        Character instrument = message.massCancelInstChar(4);
        if (instrument != null && instrument != 'B' && instrument != 'S' && instrument != 'C') return "Invalid Instrument Type Filter '" + instrument + "' in MassCancelInst";

        Character gtc = message.massCancelInstChar(5);
        if (gtc != null && gtc != 'C' && gtc != 'P') return "Invalid GTC Order Filter '" + gtc + "' in MassCancelInst";
        return null;
    }

    // Tradable universe. Must stay in sync with the catalogue exposed by
    // com.boe.simulator.api.service.SymbolService — a symbol listed there but missing here
    // shows up in GET /api/symbols and is then rejected on order entry.
    static final int MAX_MODIFICATIONS_PER_ORDER = 1_295;

    private static final Set<String> VALID_SYMBOLS = Set.of(
            "AAPL", "MSFT", "GOOGL", "GOOG", "AMZN", "META",
            "TSLA", "NVDA", "NFLX", "AMD", "DIS"
    );

    private boolean isValidSymbol(String symbol) {
        return symbol != null && VALID_SYMBOLS.contains(symbol);
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
        if (buyOrder != null) sendExecutionMessage(buyOrder, trade, trade.getAggressorSide() == Side.BUY);
        if (sellOrder != null) sendExecutionMessage(sellOrder, trade, trade.getAggressorSide() == Side.SELL);
    }

    private void sendExecutionMessage(Order order, Trade trade, boolean isAggressive) {
        if (order.getSessionSubID() == null || !order.getSessionSubID().startsWith(TcpExecutionContext.SESSION_PREFIX)) {
            LOGGER.log(Level.FINE, "Order not from a BOE session: {0} (no execution message)", order.getClOrdID());
            return;
        }
        BoeSessionState state = sessionManager.getSessionStates().latestForUser(order.getUsername());
        if (state == null) return;

        ReturnBitfields returnBitfields = state.getReturnBitfields();
        IntFunction<byte[]> encoder = seq -> {
            OrderExecutedMessage execMsg = OrderExecutedMessage.fromTrade(trade, order, isAggressive, returnBitfields);
            execMsg.setMatchingUnit(BoeSessionState.MATCHING_UNIT);
            execMsg.setSequenceNumber(seq);
            return execMsg.toBytes();
        };

        ClientConnectionHandler handler = sessionManager.getHandlerByUsername(order.getUsername());
        try {
            if (handler != null && handler.getSession().isAuthenticated()) {
                handler.sendSequenced(encoder);
                LOGGER.log(Level.INFO, "Sent execution to {0}: {1}", new Object[]{order.getUsername(), order.getClOrdID()});
            } else {
                state.sendSequenced(encoder, null);
                LOGGER.log(Level.INFO, "Journaled execution for disconnected {0}: {1}", new Object[]{order.getUsername(), order.getClOrdID()});
            }
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to send execution message to " + order.getUsername() + " (journaled for replay)", e);
        }
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

    public void reset() {
        activeOrdersByClOrdID.clear();
        activeOrdersByOrderID.clear();
        matchingEngine.reset();
        LOGGER.info("OrderManager reset: in-memory orders and order books cleared");
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

    public static class ModifyResponse {
        public enum ResponseType { MODIFIED, AUTO_CANCELLED, REJECTED }

        private final ResponseType type;
        private final Order order;
        private final String clOrdID;
        private final byte rejectReason;
        private final String rejectText;

        private ModifyResponse(ResponseType type, Order order, String clOrdID,
                               byte rejectReason, String rejectText) {
            this.type         = type;
            this.order        = order;
            this.clOrdID      = clOrdID;
            this.rejectReason = rejectReason;
            this.rejectText   = rejectText;
        }

        public static ModifyResponse modified(Order order) {
            return new ModifyResponse(ResponseType.MODIFIED, order, null, (byte) 0, null);
        }

        public static ModifyResponse autoCancelled(Order order) {
            return new ModifyResponse(ResponseType.AUTO_CANCELLED, order, null, (byte) 0, null);
        }

        public static ModifyResponse rejected(String clOrdID, byte reason, String text) {
            return new ModifyResponse(ResponseType.REJECTED, null, clOrdID, reason, text);
        }

        public static ModifyResponse rejectedAndCancelled(String clOrdID, byte reason, String text, Order cancelled) {
            return new ModifyResponse(ResponseType.REJECTED, cancelled, clOrdID, reason, text);
        }

        public boolean isModified()       { return type == ResponseType.MODIFIED; }
        public boolean isAutoCancelled()  { return type == ResponseType.AUTO_CANCELLED; }
        public boolean isRejected()       { return type == ResponseType.REJECTED; }
        public Order   getOrder()         { return order; }
        public boolean cancelledOriginal() { return type == ResponseType.REJECTED && order != null; }
        public String  getClOrdID()       { return clOrdID; }
        public byte    getRejectReason()  { return rejectReason; }
        public String  getRejectText()    { return rejectText; }
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
        private final List<Order> massCancelledOrders;
        private final char ackStyle;
        private final String massCancelId;

        private CancelResponse(ResponseType type, Order order, String clOrdID, byte reason,
                               String rejectText, List<Order> massCancelledOrders, char ackStyle, String massCancelId) {
            this.type = type;
            this.order = order;
            this.clOrdID = clOrdID;
            this.cancelReason = reason;
            this.rejectText = rejectText;
            this.massCancelledOrders = massCancelledOrders;
            this.ackStyle = ackStyle;
            this.massCancelId = massCancelId;
        }

        public static CancelResponse cancelled(Order order, byte reason) {
            return new CancelResponse(ResponseType.CANCELLED, order, null, reason, null, List.of(), 'M', null);
        }

        public static CancelResponse rejected(String clOrdID, byte reason, String text) {
            return new CancelResponse(ResponseType.REJECTED, null, clOrdID, reason, text, List.of(), 'M', null);
        }

        public static CancelResponse massCancelled(List<Order> orders, char ackStyle, String massCancelId) {
            return new CancelResponse(ResponseType.MASS_CANCELLED, null, null, OrderCancelledMessage.REASON_USER_REQUESTED,
                    null, List.copyOf(orders), ackStyle, massCancelId);
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
            return massCancelledOrders.size();
        }

        public List<Order> getMassCancelledOrders() {
            return massCancelledOrders;
        }

        // Acknowledgement Style, MassCancelInst 2nd character: M, S or B
        public char getAckStyle() {
            return ackStyle;
        }

        public byte getRejectReason() {
            return cancelReason;
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
