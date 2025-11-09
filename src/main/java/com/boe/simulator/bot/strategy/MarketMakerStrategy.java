package com.boe.simulator.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.bot.BotConfig;
import com.boe.simulator.bot.util.PriceGenerator;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.order.OrderManager;

public class MarketMakerStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(MarketMakerStrategy.class.getName());

    private final BotConfig config;
    private final Random random;
    private OrderManager orderManager;
    private MatchingEngine matchingEngine;

    public MarketMakerStrategy(BotConfig config) {
        this.config = config;
        this.random = new Random();
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

            // Place both bid and ask orders
            placeBidOrder(symbol, midPrice);
            placeAskOrder(symbol, midPrice);

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing MarketMaker for " + symbol, e);
        }
    }

    private void createInitialMarket(String symbol) {
        // Use reference price or default
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

        // Use REST API or internal order submission
        LOGGER.log(Level.FINE, "MM placing BID: {0} x {1} @ {2}",
                new Object[]{symbol, quantity, bidPrice});
    }

    private void placeAskOrder(String symbol, BigDecimal midPrice) {
        BigDecimal spread = midPrice.multiply(config.priceVariation());
        BigDecimal askPrice = midPrice.add(spread).setScale(2, RoundingMode.HALF_UP);

        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);

        LOGGER.log(Level.FINE, "MM placing ASK: {0} x {1} @ {2}",
                new Object[]{symbol, quantity, askPrice});
    }

    @Override
    public String getStrategyName() {
        return "MARKET_MAKER";
    }
}