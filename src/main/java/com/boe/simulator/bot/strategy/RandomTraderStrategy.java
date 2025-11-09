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

public class RandomTraderStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(RandomTraderStrategy.class.getName());

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

            // Random decision: 50% buy, 50% sell
            boolean isBuy = random.nextBoolean();

            BigDecimal referencePrice = getReferencePrice(book, symbol, isBuy);
            if (referencePrice == null) return;

            // Add some randomness to the price (±1%)
            BigDecimal variation = referencePrice.multiply(config.priceVariation()).multiply(BigDecimal.valueOf(random.nextDouble(-1, 1)));
            BigDecimal orderPrice = referencePrice.add(variation).setScale(2, RoundingMode.HALF_UP);

            int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);

            String side = isBuy ? "BUY" : "SELL";
            LOGGER.log(Level.FINE, "RandomTrader placing {0}: {1} x {2} @ {3}",
                    new Object[]{side, symbol, quantity, orderPrice});

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing RandomTrader for " + symbol, e);
        }
    }

    private BigDecimal getReferencePrice(OrderBook book, String symbol, boolean isBuy) {
        BigDecimal lastTradePrice = book.getLastTradePrice();

        if (lastTradePrice != null) return lastTradePrice;

        // Use best bid/ask as reference
        BigDecimal bid = book.getBestBid();
        BigDecimal ask = book.getBestAsk();

        if (isBuy && ask != null) return ask;
        else if (!isBuy && bid != null) return bid;
        else if (bid != null && ask != null) return bid.add(ask).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);


        return PriceGenerator.getDefaultPrice(symbol);
    }

    @Override
    public String getStrategyName() {
        return "RANDOM_TRADER";
    }
}