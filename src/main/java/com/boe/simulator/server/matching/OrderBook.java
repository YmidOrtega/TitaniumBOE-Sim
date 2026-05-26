package com.boe.simulator.server.matching;

import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.StampedLock;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderBook {
    private static final Logger LOGGER = Logger.getLogger(OrderBook.class.getName());

    private final String symbol;
    private final StampedLock lock = new StampedLock();

    // Bid side: descending price (the best bid first)
    private final TreeMap<BigDecimal, LinkedList<Order>> bids;

    // Ask side: ascending price (best ask first)
    private final TreeMap<BigDecimal, LinkedList<Order>> asks;

    // Index for quick search by OrderID — ConcurrentHashMap, no lock needed
    private final Map<Long, Order> orderIndex;

    private volatile BigDecimal lastTradePrice;
    private volatile int totalBidQuantity;
    private volatile int totalAskQuantity;

    public OrderBook(String symbol) {
        this.symbol = symbol;
        this.bids = new TreeMap<>(Comparator.reverseOrder());
        this.asks = new TreeMap<>();
        this.orderIndex = new ConcurrentHashMap<>();
        this.totalBidQuantity = 0;
        this.totalAskQuantity = 0;
    }

    // Private helpers — caller must already hold a read or write stamp
    private BigDecimal bestBidUnlocked() { return bids.isEmpty() ? null : bids.firstKey(); }
    private BigDecimal bestAskUnlocked() { return asks.isEmpty() ? null : asks.firstKey(); }

    public void addOrder(Order order) {
        if (!symbol.equals(order.getSymbol())) throw new IllegalArgumentException("Order symbol mismatch");

        BigDecimal price = order.getPrice();
        if (price == null) {
            LOGGER.warning("Cannot add market order to book: " + order.getClOrdID());
            return;
        }

        long stamp = lock.writeLock();
        try {
            TreeMap<BigDecimal, LinkedList<Order>> side = order.getSide() == Side.BUY ? bids : asks;
            side.computeIfAbsent(price, k -> new LinkedList<>()).addLast(order);
            orderIndex.put(order.getOrderID(), order);

            if (order.getSide() == Side.BUY) totalBidQuantity += order.getLeavesQty();
            else totalAskQuantity += order.getLeavesQty();
        } finally {
            lock.unlockWrite(stamp);
        }

        LOGGER.log(Level.FINE, "Added order to book: {0} @ {1}", new Object[]{order.getClOrdID(), price});
    }

    public boolean removeOrder(Order order) {
        long stamp = lock.writeLock();
        try {
            Order removed = orderIndex.remove(order.getOrderID());
            if (removed == null) return false;

            BigDecimal price = order.getPrice();
            TreeMap<BigDecimal, LinkedList<Order>> side = order.getSide() == Side.BUY ? bids : asks;

            LinkedList<Order> level = side.get(price);
            if (level != null) {
                boolean success = level.remove(order);
                if (level.isEmpty()) side.remove(price);

                if (order.getSide() == Side.BUY) totalBidQuantity -= order.getLeavesQty();
                else totalAskQuantity -= order.getLeavesQty();

                LOGGER.log(Level.FINE, "Removed order from book: {0}", order.getClOrdID());
                return success;
            }
            return false;
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    public BigDecimal getBestBid() {
        long stamp = lock.tryOptimisticRead();
        BigDecimal result = bestBidUnlocked();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { result = bestBidUnlocked(); } finally { lock.unlockRead(stamp); }
        }
        return result;
    }

    public BigDecimal getBestAsk() {
        long stamp = lock.tryOptimisticRead();
        BigDecimal result = bestAskUnlocked();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { result = bestAskUnlocked(); } finally { lock.unlockRead(stamp); }
        }
        return result;
    }

    public BigDecimal getSpread() {
        long stamp = lock.readLock();
        try {
            BigDecimal bid = bestBidUnlocked();
            BigDecimal ask = bestAskUnlocked();
            return (bid == null || ask == null) ? null : ask.subtract(bid);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public BigDecimal getMidPrice() {
        long stamp = lock.readLock();
        try {
            BigDecimal bid = bestBidUnlocked();
            BigDecimal ask = bestAskUnlocked();
            return (bid == null || ask == null) ? null : bid.add(ask).divide(BigDecimal.valueOf(2));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public List<Order> getTopBidOrders() {
        long stamp = lock.readLock();
        try {
            BigDecimal bestBid = bestBidUnlocked();
            if (bestBid == null) return List.of();
            return new ArrayList<>(bids.get(bestBid));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public List<Order> getTopAskOrders() {
        long stamp = lock.readLock();
        try {
            BigDecimal bestAsk = bestAskUnlocked();
            if (bestAsk == null) return List.of();
            return new ArrayList<>(asks.get(bestAsk));
        } finally {
            lock.unlockRead(stamp);
        }
    }

    public Order findOrder(long orderID) {
        return orderIndex.get(orderID);
    }

    public boolean isEmpty() {
        long stamp = lock.tryOptimisticRead();
        boolean result = bids.isEmpty() && asks.isEmpty();
        if (!lock.validate(stamp)) {
            stamp = lock.readLock();
            try { result = bids.isEmpty() && asks.isEmpty(); } finally { lock.unlockRead(stamp); }
        }
        return result;
    }

    public int size() {
        return orderIndex.size();
    }

    public BookSnapshot getSnapshot(int depth) {
        long stamp = lock.readLock();
        try {
            List<PriceLevel> bidLevels = new ArrayList<>();
            List<PriceLevel> askLevels = new ArrayList<>();

            int count = 0;
            for (Map.Entry<BigDecimal, LinkedList<Order>> entry : bids.entrySet()) {
                if (count++ >= depth) break;
                int totalQty = entry.getValue().stream().mapToInt(Order::getLeavesQty).sum();
                bidLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }

            count = 0;
            for (Map.Entry<BigDecimal, LinkedList<Order>> entry : asks.entrySet()) {
                if (count++ >= depth) break;
                int totalQty = entry.getValue().stream().mapToInt(Order::getLeavesQty).sum();
                askLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
            }

            return new BookSnapshot(symbol, bidLevels, askLevels, lastTradePrice);
        } finally {
            lock.unlockRead(stamp);
        }
    }

    // Getters
    public String getSymbol() { return symbol; }
    public BigDecimal getLastTradePrice() { return lastTradePrice; }
    public int getTotalBidQuantity() { return totalBidQuantity; }
    public int getTotalAskQuantity() { return totalAskQuantity; }

    public void setLastTradePrice(BigDecimal price) {
        this.lastTradePrice = price;
    }

    @Override
    public String toString() {
        return String.format("OrderBook[%s]{bid=%s, ask=%s, orders=%d}",
                symbol, getBestBid(), getBestAsk(), size());
    }

    public record PriceLevel(BigDecimal price, int quantity, int orderCount) {
        @Override
        public String toString() {
            return String.format("%s x %d (%d orders)", price, quantity, orderCount);
        }
    }

    public record BookSnapshot(
            String symbol,
            List<PriceLevel> bids,
            List<PriceLevel> asks,
            BigDecimal lastTradePrice
    ) {
        public void print() {
            System.out.println("\n=== Order Book: " + symbol + " ===");
            System.out.println("Last Trade: " + lastTradePrice);
            System.out.println("\nASKS:");
            for (int i = asks.size() - 1; i >= 0; i--) {
                System.out.println("  " + asks.get(i));
            }
            System.out.println("-------------------");
            System.out.println("BIDS:");
            for (PriceLevel bid : bids) {
                System.out.println("  " + bid);
            }
            System.out.println("===================\n");
        }
    }
}