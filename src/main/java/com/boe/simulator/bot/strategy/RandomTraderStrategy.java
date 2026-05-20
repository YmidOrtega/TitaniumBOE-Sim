package com.boe.simulator.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

import com.boe.simulator.bot.BotConfig;
import com.boe.simulator.bot.util.PriceGenerator;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.order.OrderManager;

public class RandomTraderStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(RandomTraderStrategy.class.getName());

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final BotConfig config;
    private final Random random;
    private OrderManager orderManager;
    private MatchingEngine matchingEngine;

    public RandomTraderStrategy(BotConfig config) {
        this.config = config;
        this.random = new Random();
    }

    @Override
    public void initialize(OrderManager orderManager, MatchingEngine matchingEngine) {
        this.orderManager = orderManager;
        this.matchingEngine = matchingEngine;
        LOGGER.log(Level.INFO, "RandomTrader initialized for symbols: {0}", config.symbols());
    }

    @Override
    public void execute(String symbol) {
        try {
            var bookOpt = matchingEngine.getOrderBook(symbol);

            if (bookOpt.isEmpty()) {
                LOGGER.log(Level.FINE, "No order book for {0}, skipping", symbol);
                return;
            }

            OrderBook book = bookOpt.get();

            // 60% aggressive taker, 40% passive
            boolean aggressive = random.nextDouble() < 0.60;
            // Random decision: 50% buy, 50% sell
            boolean isBuy = random.nextBoolean();
            int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);

            if (aggressive) {
                placeAggressiveOrder(symbol, book, isBuy, quantity);
            } else {
                placePassiveOrder(symbol, book, isBuy, quantity);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing RandomTrader for " + symbol, e);
        }
    }

    // Aggressive taker: cross the spread to generate matches
    private void placeAggressiveOrder(String symbol, OrderBook book, boolean isBuy, int quantity) {
        if (isBuy) {
            BigDecimal bestAsk = book.getBestAsk();
            if (bestAsk == null) return; // No ask to buy against — skip
            // Price slightly above best ask to ensure it crosses
            BigDecimal aggressivePrice = bestAsk.add(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            submitOrder(symbol, (byte) 1, quantity, aggressivePrice, (byte) 0); // Day
        } else {
            BigDecimal bestBid = book.getBestBid();
            if (bestBid == null) return; // No bid to sell against — skip
            // Price slightly below best bid to ensure it crosses
            BigDecimal aggressivePrice = bestBid.subtract(new BigDecimal("0.01")).setScale(2, RoundingMode.HALF_UP);
            submitOrder(symbol, (byte) 2, quantity, aggressivePrice, (byte) 0); // Day
        }
    }

    // Passive maker: add liquidity at mid ± variation
    private void placePassiveOrder(String symbol, OrderBook book, boolean isBuy, int quantity) {
        BigDecimal referencePrice = getMidOrDefault(book, symbol);
        if (referencePrice == null) return;

        BigDecimal variation = referencePrice.multiply(config.priceVariation())
                .multiply(BigDecimal.valueOf(random.nextDouble(-1, 1)));
        BigDecimal orderPrice = referencePrice.add(variation).setScale(2, RoundingMode.HALF_UP);

        submitOrder(symbol, isBuy ? (byte) 1 : (byte) 2, quantity, orderPrice, (byte) 0); // Day
    }

    private BigDecimal getMidOrDefault(OrderBook book, String symbol) {
        BigDecimal lastTradePrice = book.getLastTradePrice();
        if (lastTradePrice != null) return lastTradePrice;

        BigDecimal bid = book.getBestBid();
        BigDecimal ask = book.getBestAsk();

        if (bid != null && ask != null) return bid.add(ask).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (bid != null) return bid;
        if (ask != null) return ask;

        return PriceGenerator.getDefaultPrice(symbol);
    }

    private void submitOrder(String symbol, byte side, int qty, BigDecimal price, byte timeInForce) {
        if (orderManager == null) return;
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(String.format("RT%018d", SEQ.incrementAndGet()));
        msg.setSide(side);
        msg.setOrderQty(qty);
        msg.setPrice(price);
        msg.setSymbol(symbol);
        msg.setOrdType((byte) 2);           // Limit
        msg.setTimeInForce(timeInForce);
        msg.setCapacity((byte) 'C');        // Customer
        orderManager.processNewOrder(msg, "BOT-RAND");
    }

    @Override
    public String getStrategyName() {
        return "RANDOM_TRADER";
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public void setOrderManager(OrderManager orderManager) {
        this.orderManager = orderManager;
    }
}
