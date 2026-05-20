package com.boe.simulator.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import com.boe.simulator.bot.BotConfig;
import com.boe.simulator.bot.util.PriceGenerator;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.order.OrderManager;

public class MarketMakerStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(MarketMakerStrategy.class.getName());

    private static final AtomicLong SEQ = new AtomicLong(0);

    private final BotConfig config;
    private final Random random;
    private final AtomicInteger orderCount;
    private OrderManager orderManager;
    private MatchingEngine matchingEngine;

    public MarketMakerStrategy(BotConfig config) {
        this.config = config;
        this.random = new Random();
        this.orderCount = new AtomicInteger(0);
    }

    @Override
    public void initialize(OrderManager orderManager, MatchingEngine matchingEngine) {
        this.orderManager = orderManager;
        this.matchingEngine = matchingEngine;
        LOGGER.log(Level.INFO, "MarketMaker initialized for symbols: {0}", config.symbols());
    }

    @Override
    public void execute(String symbol) {
        try {
            var bookOpt = matchingEngine.getOrderBook(symbol);

            if (bookOpt.isEmpty()) {
                // No book exists, create an initial market
                createInitialMarket(symbol);
                return;
            }

            OrderBook book = bookOpt.get();
            BigDecimal midPrice = calculateMidPrice(book, symbol);

            if (midPrice == null) {
                LOGGER.log(Level.WARNING, "Cannot determine mid price for {0}", symbol);
                return;
            }

            int cycle = orderCount.incrementAndGet();

            // Place passive bid and ask orders with tight spread
            placeBidOrder(symbol, midPrice);
            placeAskOrder(symbol, midPrice);

            // Every 5th cycle, also place an aggressive IOC order to spark activity
            if (cycle % 5 == 0) {
                BigDecimal bestBid = book.getBestBid();
                BigDecimal bestAsk = book.getBestAsk();

                // Flip a coin: aggressive buy or aggressive sell
                if (random.nextBoolean()) {
                    placeAggressiveBuy(symbol, bestAsk);
                } else {
                    placeAggressiveSell(symbol, bestBid);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing MarketMaker for " + symbol, e);
        }
    }

    private void createInitialMarket(String symbol) {
        BigDecimal basePrice = PriceGenerator.getDefaultPrice(symbol);

        placeBidOrder(symbol, basePrice);
        placeAskOrder(symbol, basePrice);

        LOGGER.log(Level.INFO, "Created initial market for {0} @ {1}", new Object[]{symbol, basePrice});
    }

    private BigDecimal calculateMidPrice(OrderBook book, String symbol) {
        BigDecimal bid = book.getBestBid();
        BigDecimal ask = book.getBestAsk();

        if (bid != null && ask != null) return bid.add(ask).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        else if (bid != null) return bid.multiply(BigDecimal.valueOf(1.01));
        else if (ask != null) return ask.multiply(BigDecimal.valueOf(0.99));
        else return PriceGenerator.getDefaultPrice(symbol);
    }

    private void placeBidOrder(String symbol, BigDecimal midPrice) {
        BigDecimal spread = midPrice.multiply(config.priceVariation());
        BigDecimal bidPrice = midPrice.subtract(spread).setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
        submitOrder(symbol, (byte) 1, quantity, bidPrice, (byte) 0);
    }

    private void placeAskOrder(String symbol, BigDecimal midPrice) {
        BigDecimal spread = midPrice.multiply(config.priceVariation());
        BigDecimal askPrice = midPrice.add(spread).setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
        submitOrder(symbol, (byte) 2, quantity, askPrice, (byte) 0);
    }

    // Place an IOC aggressive buy order slightly above best ask to match resting sellers
    private void placeAggressiveBuy(String symbol, BigDecimal bestAsk) {
        if (bestAsk == null) return;
        BigDecimal aggressivePrice = bestAsk.multiply(BigDecimal.valueOf(1.001)).setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
        submitOrder(symbol, (byte) 1, quantity, aggressivePrice, (byte) 3); // IOC
    }

    // Place an IOC aggressive sell order slightly below best bid to match resting buyers
    private void placeAggressiveSell(String symbol, BigDecimal bestBid) {
        if (bestBid == null) return;
        BigDecimal aggressivePrice = bestBid.multiply(BigDecimal.valueOf(0.999)).setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
        submitOrder(symbol, (byte) 2, quantity, aggressivePrice, (byte) 3); // IOC
    }

    private void submitOrder(String symbol, byte side, int qty, BigDecimal price, byte timeInForce) {
        if (orderManager == null) return;
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(String.format("MM%018d", SEQ.incrementAndGet()));
        msg.setSide(side);
        msg.setOrderQty(qty);
        msg.setPrice(price);
        msg.setSymbol(symbol);
        msg.setOrdType((byte) 2);           // Limit
        msg.setTimeInForce(timeInForce);
        msg.setCapacity((byte) 'M');        // Market Maker
        orderManager.processNewOrder(msg, "BOT-MM");
    }

    @Override
    public String getStrategyName() {
        return "MARKET_MAKER";
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public void setOrderManager(OrderManager orderManager) {
        this.orderManager = orderManager;
    }
}
