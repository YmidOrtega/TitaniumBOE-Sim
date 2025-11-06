package com.boe.simulator.server.matching;

import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class OrderBook {
    private static final Logger LOGGER = Logger.getLogger(OrderBook.class.getName());

    private final String symbol;

    // Bid side: descending price (the best bid first)
    private final TreeMap<BigDecimal, LinkedList<Order>> bids;

    // Ask side: ascending price (best ask first)
    private final TreeMap<BigDecimal, LinkedList<Order>> asks;

    // Index for quick search by OrderID
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

    public synchronized void addOrder(Order order) {
        if (!symbol.equals(order.getSymbol())) throw new IllegalArgumentException("Order symbol mismatch");

        BigDecimal price = order.getPrice();
        if (price == null) {
            LOGGER.warning("Cannot add market order to book: " + order.getClOrdID());
            return;
        }

        TreeMap<BigDecimal, LinkedList<Order>> side = order.getSide() == 1 ? bids : asks;

        side.computeIfAbsent(price, k -> new LinkedList<>()).addLast(order);
        orderIndex.put(order.getOrderID(), order);

        if (order.getSide() == 1) totalBidQuantity += order.getLeavesQty();
        else totalAskQuantity += order.getLeavesQty();

        LOGGER.log(Level.FINE, "Added order to book: {0} @ {1}",
                new Object[]{order.getClOrdID(), price});
    }

    public synchronized boolean removeOrder(Order order) {
        Order removed = orderIndex.remove(order.getOrderID());
        if (removed == null) return false;

        BigDecimal price = order.getPrice();
        TreeMap<BigDecimal, LinkedList<Order>> side = order.getSide() == 1 ? bids : asks;

        LinkedList<Order> level = side.get(price);
        if (level != null) {
            boolean success = level.remove(order);
            if (level.isEmpty()) side.remove(price);

            if (order.getSide() == 1) totalBidQuantity -= order.getLeavesQty();
            else totalAskQuantity -= order.getLeavesQty();

            LOGGER.log(Level.FINE, "Removed order from book: {0}", order.getClOrdID());
            return success;
        }

        return false;
    }

    public synchronized BigDecimal getBestBid() {
        return bids.isEmpty() ? null : bids.firstKey();
    }

    public synchronized BigDecimal getBestAsk() {
        return asks.isEmpty() ? null : asks.firstKey();
    }

    public synchronized BigDecimal getSpread() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();

        if (bid == null || ask == null) return null;

        return ask.subtract(bid);
    }

    public synchronized BigDecimal getMidPrice() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();

        if (bid == null || ask == null) {
            return null;
        }

        return bid.add(ask).divide(BigDecimal.valueOf(2));
    }

    public synchronized List<Order> getTopBidOrders() {
        BigDecimal bestBid = getBestBid();
        if (bestBid == null) {
            return List.of();
        }
        return new ArrayList<>(bids.get(bestBid));
    }

    public synchronized List<Order> getTopAskOrders() {
        BigDecimal bestAsk = getBestAsk();
        if (bestAsk == null) {
            return List.of();
        }
        return new ArrayList<>(asks.get(bestAsk));
    }

    public Order findOrder(long orderID) {
        return orderIndex.get(orderID);
    }

    public synchronized boolean isEmpty() {
        return bids.isEmpty() && asks.isEmpty();
    }

    public synchronized int size() {
        return orderIndex.size();
    }

    public synchronized BookSnapshot getSnapshot(int depth) {
        List<PriceLevel> bidLevels = new ArrayList<>();
        List<PriceLevel> askLevels = new ArrayList<>();

        int count = 0;
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : bids.entrySet()) {
            if (count++ >= depth) break;

            int totalQty = entry.getValue().stream()
                    .mapToInt(Order::getLeavesQty)
                    .sum();

            bidLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
        }

        count = 0;
        for (Map.Entry<BigDecimal, LinkedList<Order>> entry : asks.entrySet()) {
            if (count++ >= depth) break;

            int totalQty = entry.getValue().stream()
                    .mapToInt(Order::getLeavesQty)
                    .sum();

            askLevels.add(new PriceLevel(entry.getKey(), totalQty, entry.getValue().size()));
        }

        return new BookSnapshot(symbol, bidLevels, askLevels, lastTradePrice);
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