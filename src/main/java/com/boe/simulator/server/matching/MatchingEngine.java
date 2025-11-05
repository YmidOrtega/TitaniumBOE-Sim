package com.boe.simulator.server.matching;

import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;

public class MatchingEngine {
    private static final Logger LOGGER = Logger.getLogger(MatchingEngine.class.getName());

    private final ConcurrentHashMap<String, OrderBook> orderBooks;

    // Listener to notify executions
    private final List<TradeListener> tradeListeners;

    private long totalTrades = 0;
    private long totalVolume = 0;

    public MatchingEngine() {
        this.orderBooks = new ConcurrentHashMap<>();
        this.tradeListeners = new ArrayList<>();
        LOGGER.info("MatchingEngine initialized with FIFO (Price-Time Priority)");
    }

    public MatchResult processOrder(Order order) {
        if (order == null) throw new IllegalArgumentException("Order cannot be null");

        String symbol = order.getSymbol();

        // Obtener o crear OrderBook para el símbolo
        OrderBook book = orderBooks.computeIfAbsent(symbol, k -> new OrderBook(symbol));

        LOGGER.log(Level.INFO, "Processing order in matching engine: {0}", order.getClOrdID());

        // Try to match
        MatchResult result = book.match(order);

        // Update statistics
        if (!result.getTrades().isEmpty()) {
            totalTrades += result.getTrades().size();
            totalVolume += result.getTrades().stream()
                    .mapToLong(Trade::getQuantity)
                    .sum();

            // Notify listeners
            result.getTrades().forEach(this::notifyTradeListeners);
        }

        LOGGER.log(Level.INFO, "Match result: {0} trades, order status: {1}",
                new Object[]{result.getTrades().size(), result.getOrderStatus()});

        return result;
    }

    public boolean cancelOrder(Order order) {
        String symbol = order.getSymbol();
        OrderBook book = orderBooks.get(symbol);

        if (book == null) {
            LOGGER.log(Level.WARNING, "No order book found for symbol: {0}", symbol);
            return false;
        }

        return book.removeOrder(order);
    }

    public Optional<OrderBookSnapshot> getOrderBook(String symbol) {
        OrderBook book = orderBooks.get(symbol);
        return Optional.ofNullable(book).map(OrderBook::getSnapshot);
    }

    public void addTradeListener(TradeListener listener) {
        tradeListeners.add(listener);
    }

    private void notifyTradeListeners(Trade trade) {
        for (TradeListener listener : tradeListeners) {
            try {
                listener.onTrade(trade);
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error notifying trade listener", e);
            }
        }
    }

    // Getters para estadísticas
    public long getTotalTrades() { return totalTrades; }

    public long getTotalVolume() { return totalVolume; }

    public int getOrderBookCount() { return orderBooks.size(); }

    public Map<String, OrderBookSnapshot> getAllOrderBooks() {
        Map<String, OrderBookSnapshot> snapshots = new HashMap<>();
        orderBooks.forEach((symbol, book) -> snapshots.put(symbol, book.getSnapshot()));
        return snapshots;
    }

    private static class OrderBook {
        private final String symbol;

        // Bids: sorted by best price (descending), then by time
        private final PriorityBlockingQueue<Order> bids;

        // Asks: sorted by best price (ascending), then by time
        private final PriorityBlockingQueue<Order> asks;

        public OrderBook(String symbol) {
            this.symbol = symbol;

            // Comparator for BIDs: DESC price, then ASC timestamp (FIFO)
            Comparator<Order> bidComparator = Comparator
                    .comparing(Order::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Order::getCreatedAt);
            this.bids = new PriorityBlockingQueue<>(100, bidComparator);

            // Comparator for ASKs: ASC price, then ASC timestamp (FIFO)
            Comparator<Order> askComparator = Comparator
                    .comparing(Order::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Order::getCreatedAt);
            this.asks = new PriorityBlockingQueue<>(100, askComparator);

            LOGGER.log(Level.INFO, "Created OrderBook for symbol: {0}", symbol);
        }

        public synchronized MatchResult match(Order incomingOrder) {
            List<Trade> trades = new ArrayList<>();

            // Determinar en qué lado está la orden
            boolean isBuy = incomingOrder.getSide() == 1;
            PriorityBlockingQueue<Order> oppositeBook = isBuy ? asks : bids;
            PriorityBlockingQueue<Order> ownBook = isBuy ? bids : asks;

            int remainingQty = incomingOrder.getOrderQty();

            // Attempt matching while there is still quantity remaining
            while (remainingQty > 0 && !oppositeBook.isEmpty()) {
                Order bookOrder = oppositeBook.peek();

                if (bookOrder == null || !canMatch(incomingOrder, bookOrder)) break;

                // Calculate quantity to be executed
                int execQty = Math.min(remainingQty, bookOrder.getLeavesQty());

                // Execution price: the price of the order that is in the book
                BigDecimal execPrice = bookOrder.getPrice();

                // Create trade
                Trade trade = new Trade(
                        symbol,
                        execQty,
                        execPrice,
                        isBuy ? incomingOrder : bookOrder,  // aggressor
                        isBuy ? bookOrder : incomingOrder   // passive
                );
                trades.add(trade);

                // Update orders
                remainingQty -= execQty;
                incomingOrder.fill(execQty, execPrice);

                int bookOrderRemainingQty = bookOrder.getLeavesQty() - execQty;
                bookOrder.fill(execQty, execPrice);

                // If the book order has been completed, remove it.
                if (bookOrder.isFilled()) oppositeBook.poll();

                LOGGER.log(Level.INFO, "Trade executed: {0} @ {1} ({2} x {3})", new Object[]{
                        symbol,
                        execPrice,
                        execQty,
                        isBuy ? "BUY" : "SELL"
                });
            }

            // If there is quantity left, and it is a limit order, add it to the order book.
            OrderStatus status;
            if (remainingQty > 0 && incomingOrder.getOrdType() == 2) { // Limit order
                ownBook.offer(incomingOrder);
                status = trades.isEmpty() ? OrderStatus.BOOKED : OrderStatus.PARTIALLY_FILLED;
            } else if (remainingQty > 0) {
                incomingOrder.cancel();
                status = OrderStatus.CANCELLED;
            } else status = OrderStatus.FILLED;


            return new MatchResult(trades, status, remainingQty);
        }

        private boolean canMatch(Order incoming, Order book) {
            // Orders must be from opposite sides.
            if (incoming.getSide() == book.getSide()) return false;

            // Market orders always match (if the order book is limit).
            if (incoming.getOrdType() == 1) return true; // Market order

            // For limit orders, verify price crossing.
            if (incoming.getPrice() == null || book.getPrice() == null) return false;


            boolean isBuy = incoming.getSide() == 1;

            return isBuy ? incoming.getPrice().compareTo(book.getPrice()) >= 0 : incoming.getPrice().compareTo(book.getPrice()) <= 0;
        }

        public synchronized boolean removeOrder(Order order) {
            boolean removed = bids.remove(order) || asks.remove(order);
            if (removed) LOGGER.log(Level.INFO, "Order removed from book: {0}", order.getClOrdID());

            return removed;
        }

        public OrderBookSnapshot getSnapshot() {
            List<Order> bidsList = new ArrayList<>(bids);
            List<Order> asksList = new ArrayList<>(asks);

            bidsList.sort(Comparator
                    .comparing(Order::getPrice, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Order::getCreatedAt));

            asksList.sort(Comparator
                    .comparing(Order::getPrice, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(Order::getCreatedAt));

            return new OrderBookSnapshot(symbol, bidsList, asksList);
        }
    }

    public static class MatchResult {
        private final List<Trade> trades;
        private final OrderStatus orderStatus;
        private final int remainingQty;

        public MatchResult(List<Trade> trades, OrderStatus orderStatus, int remainingQty) {
            this.trades = List.copyOf(trades);
            this.orderStatus = orderStatus;
            this.remainingQty = remainingQty;
        }

        public List<Trade> getTrades() { return trades; }

        public OrderStatus getOrderStatus() { return orderStatus; }

        public int getRemainingQty() { return remainingQty; }

        public boolean hasExecutions() { return !trades.isEmpty(); }
    }

    public enum OrderStatus {
        FILLED,
        PARTIALLY_FILLED,
        BOOKED,
        CANCELLED
    }

    public static class Trade {
        private final String symbol;
        private final int quantity;
        private final BigDecimal price;
        private final Order aggressorOrder;   // La orden que entró
        private final Order passiveOrder;     // La orden que estaba en el libro
        private final long timestamp;

        public Trade(String symbol, int quantity, BigDecimal price, Order aggressorOrder, Order passiveOrder) {
            this.symbol = symbol;
            this.quantity = quantity;
            this.price = price;
            this.aggressorOrder = aggressorOrder;
            this.passiveOrder = passiveOrder;
            this.timestamp = System.currentTimeMillis();
        }

        public String getSymbol() { return symbol; }

        public int getQuantity() { return quantity; }

        public BigDecimal getPrice() { return price; }

        public Order getAggressorOrder() { return aggressorOrder; }

        public Order getPassiveOrder() { return passiveOrder; }

        public long getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return String.format("Trade{%s: %d @ %s, aggressor=%s, passive=%s}",
                    symbol, quantity, price,
                    aggressorOrder.getClOrdID(),
                    passiveOrder.getClOrdID());
        }
    }

    public record OrderBookSnapshot(
            String symbol,
            List<Order> bids,
            List<Order> asks
    ) {
        public OrderBookSnapshot {
            bids = List.copyOf(bids);
            asks = List.copyOf(asks);
        }

        public int getBidDepth() { return bids.size(); }

        public int getAskDepth() { return asks.size(); }

        public Optional<BigDecimal> getBestBid() {
            return bids.isEmpty() ? Optional.empty() : Optional.ofNullable(bids.getFirst().getPrice());
        }

        public Optional<BigDecimal> getBestAsk() {
            return asks.isEmpty() ? Optional.empty() : Optional.ofNullable(asks.getFirst().getPrice());
        }

        public Optional<BigDecimal> getSpread() {
            var bestBid = getBestBid();
            var bestAsk = getBestAsk();

            if (bestBid.isPresent() && bestAsk.isPresent()) return Optional.of(bestAsk.get().subtract(bestBid.get()));

            return Optional.empty();
        }

        @Override
        public String toString() {
            return String.format("OrderBook[%s]: Bids=%d, Asks=%d, Best=%s/%s, Spread=%s",
                    symbol,
                    bids.size(),
                    asks.size(),
                    getBestBid().map(Object::toString).orElse("-"),
                    getBestAsk().map(Object::toString).orElse("-"),
                    getSpread().map(Object::toString).orElse("-"));
        }
    }

    @FunctionalInterface
    public interface TradeListener {
        void onTrade(Trade trade);
    }
}