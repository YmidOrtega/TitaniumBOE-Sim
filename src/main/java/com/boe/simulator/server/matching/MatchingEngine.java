package com.boe.simulator.server.matching;

import com.boe.simulator.api.websocket.WebSocketService;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderRepository;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MatchingEngine {
    private static final Logger LOGGER = Logger.getLogger(MatchingEngine.class.getName());

    private final Map<String, OrderBook> orderBooks;
    private final OrderRepository orderRepository;
    private final TradeRepository tradeRepository;
    private final AtomicLong tradeIdGenerator;
    private final List<MatchingEventListener> eventListeners;
    private final boolean allowSelfTrade;
    private final AtomicLong totalMatches;
    private final AtomicLong totalTradeVolume;
    private WebSocketService webSocketService;

    public MatchingEngine(OrderRepository orderRepository, TradeRepository tradeRepository) {
        this(orderRepository, tradeRepository, false);
    }

    public MatchingEngine(OrderRepository orderRepository, TradeRepository tradeRepository, boolean allowSelfTrade) {
        this.orderBooks = new ConcurrentHashMap<>();
        this.orderRepository = orderRepository;
        this.tradeRepository = tradeRepository;
        this.tradeIdGenerator = new AtomicLong(1000000);
        this.eventListeners = new ArrayList<>();
        this.allowSelfTrade = allowSelfTrade;
        this.totalMatches = new AtomicLong(0);
        this.totalTradeVolume = new AtomicLong(0);

        LOGGER.info("MatchingEngine initialized (Self-trade: " + allowSelfTrade + ")");
    }

    public void setWebSocketService(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
    }

    public List<Trade> processOrder(Order order) {
        String symbol = order.getSymbol();
        OrderBook book = orderBooks.computeIfAbsent(symbol, OrderBook::new);

        List<Trade> trades = new ArrayList<>();

        // If it is a market order or can be matched immediately, attempt matching.
        if (canMatch(order, book)) trades = executeMatching(order, book);

        // If there is an outstanding amount, add it to the book.
        if (order.getLeavesQty() > 0 && order.isLive()) {
            book.addOrder(order);
            orderRepository.save(order);
            notifyOrderAdded(order, book);

            LOGGER.log(Level.INFO, "Order added to book: {0} ({1} @ {2})",
                    new Object[]{order.getClOrdID(), order.getLeavesQty(), order.getPrice()});
        }

        return trades;
    }

    public boolean cancelOrder(Order order) {
        OrderBook book = orderBooks.get(order.getSymbol());
        if (book == null) return false;

        boolean removed = book.removeOrder(order);
        if (removed) {
            orderRepository.save(order);
            notifyOrderRemoved(order, book);
            LOGGER.log(Level.INFO, "Order cancelled from book: {0}", order.getClOrdID());
        }

        return removed;
    }

    private boolean canMatch(Order incomingOrder, OrderBook book) {
        if (incomingOrder.getOrdType() == 1) return true; // Market order

        BigDecimal incomingPrice = incomingOrder.getPrice();
        if (incomingPrice == null) return false;

        if (incomingOrder.getSide() == 1) { // Buy
            BigDecimal bestAsk = book.getBestAsk();
            return bestAsk != null && incomingPrice.compareTo(bestAsk) >= 0;
        } else { // Sell
            BigDecimal bestBid = book.getBestBid();
            return bestBid != null && incomingPrice.compareTo(bestBid) <= 0;
        }
    }

    private List<Trade> executeMatching(Order aggressiveOrder, OrderBook book) {
        List<Trade> trades = new ArrayList<>();

        while (aggressiveOrder.getLeavesQty() > 0 && canMatch(aggressiveOrder, book)) {
            List<Order> passiveOrders = aggressiveOrder.getSide() == 1 ? book.getTopAskOrders() : book.getTopBidOrders();

            if (passiveOrders.isEmpty()) break;

            // FIFO: take the first order from the level
            Order passiveOrder = passiveOrders.get(0);

            String aggressiveUsername = aggressiveOrder.getUsername();
            String passiveUsername = passiveOrder.getUsername();

            // Verify self-trade
            if (!allowSelfTrade && aggressiveUsername != null && passiveUsername != null) {
                if (aggressiveUsername.equals(passiveUsername)) {
                    LOGGER.log(Level.WARNING, "Self-trade prevented: {0}", aggressiveUsername);
                    book.removeOrder(passiveOrder);
                    passiveOrder.cancel();
                    orderRepository.save(passiveOrder);
                    continue;
                }
            }

            // Calculate quantity to be executed
            int fillQty = Math.min(aggressiveOrder.getLeavesQty(), passiveOrder.getLeavesQty());

            // Execution price is the price of the passive order (price-time priority).
            BigDecimal execPrice = passiveOrder.getPrice();

            // Create trade
            Trade trade = createTrade(aggressiveOrder, passiveOrder, fillQty, execPrice);
            trades.add(trade);

            // Update orders
            aggressiveOrder.fill(fillQty, execPrice);
            passiveOrder.fill(fillQty, execPrice);

            // Update last trade price
            book.setLastTradePrice(execPrice);

            orderRepository.save(aggressiveOrder);
            orderRepository.save(passiveOrder);
            tradeRepository.save(trade);

            notifyTradeExecuted(trade, book);

            // If the passive order has been completed, remove it from the book.
            if (passiveOrder.getLeavesQty() == 0) book.removeOrder(passiveOrder);

            // Update statistics
            totalMatches.incrementAndGet();
            totalTradeVolume.addAndGet(fillQty);

            LOGGER.log(Level.INFO, "Trade executed: {0} x {1} @ {2}", new Object[]{
                    trade.getSymbol(),
                    fillQty,
                    execPrice
            });
        }

        return trades;
    }

    private Trade createTrade(Order aggressive, Order passive, int qty, BigDecimal price) {
        long tradeId = tradeIdGenerator.getAndIncrement();

        boolean aggressiveIsBuy = aggressive.getSide() == 1;

        return Trade.builder()
                .tradeId(tradeId)
                .symbol(aggressive.getSymbol())
                .buyOrderId(aggressiveIsBuy ? aggressive.getOrderID() : passive.getOrderID())
                .buyClOrdID(aggressiveIsBuy ? aggressive.getClOrdID() : passive.getClOrdID())
                .buyUsername(aggressiveIsBuy ? aggressive.getUsername() : passive.getUsername())
                .sellOrderId(aggressiveIsBuy ? passive.getOrderID() : aggressive.getOrderID())
                .sellClOrdID(aggressiveIsBuy ? passive.getClOrdID() : aggressive.getClOrdID())
                .sellUsername(aggressiveIsBuy ? passive.getUsername() : aggressive.getUsername())
                .quantity(qty)
                .price(price)
                .matchingUnit(aggressive.getMatchingUnit())
                .clearingFirm(aggressive.getClearingFirm())
                .build();
    }

    public Optional<OrderBook> getOrderBook(String symbol) {
        return Optional.ofNullable(orderBooks.get(symbol));
    }

    public Set<String> getActiveSymbols() {
        return new HashSet<>(orderBooks.keySet());
    }

    public void addEventListener(MatchingEventListener listener) {
        eventListeners.add(listener);
    }

    public void removeEventListener(MatchingEventListener listener) {
        eventListeners.remove(listener);
    }

    // Methods of notification
    private void notifyTradeExecuted(Trade trade, OrderBook book) {
        for (MatchingEventListener listener : eventListeners) {
            try {
                listener.onTradeExecuted(trade, book);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying listener", e);
            }
        }
    }

    private void notifyOrderAdded(Order order, OrderBook book) {
        eventListeners.forEach(listener -> listener.onOrderAdded(order, book));

        if (webSocketService != null) webSocketService.broadcastOrderBookUpdate(order.getSymbol(), book, 10);
    }

    private void notifyOrderRemoved(Order order, OrderBook book) {
        eventListeners.forEach(listener -> listener.onOrderRemoved(order, book));

        if (webSocketService != null) webSocketService.broadcastOrderBookUpdate(order.getSymbol(), book, 10);
    }

    // Statistics
    public long getTotalMatches() {
        return totalMatches.get();
    }

    public long getTotalTradeVolume() {
        return totalTradeVolume.get();
    }

    public int getTotalOrdersInBooks() {
        return orderBooks.values().stream()
                .mapToInt(OrderBook::size)
                .sum();
    }

    public void printStatistics() {
        LOGGER.info("========== Matching Engine Statistics ==========");
        LOGGER.log(Level.INFO, "Active Symbols: {0}", orderBooks.size());
        LOGGER.log(Level.INFO, "Total Orders in Books: {0}", getTotalOrdersInBooks());
        LOGGER.log(Level.INFO, "Total Matches: {0}", totalMatches.get());
        LOGGER.log(Level.INFO, "Total Trade Volume: {0}", totalTradeVolume.get());
        LOGGER.info("===============================================");
    }

    public interface MatchingEventListener {
        default void onTradeExecuted(Trade trade, OrderBook book) {}
        default void onOrderAdded(Order order, OrderBook book) {}
        default void onOrderRemoved(Order order, OrderBook book) {}
    }
}