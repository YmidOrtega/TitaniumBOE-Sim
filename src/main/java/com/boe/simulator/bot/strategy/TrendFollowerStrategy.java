package com.boe.simulator.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.bot.BotConfig;
import com.boe.simulator.bot.util.PriceGenerator;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.order.OrderManager;

public class TrendFollowerStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(TrendFollowerStrategy.class.getName());

    private final BotConfig config;
    private final Random random;
    private OrderManager orderManager;
    private MatchingEngine matchingEngine;
    private TradeRepository tradeRepository;

    public TrendFollowerStrategy(BotConfig config, TradeRepository tradeRepository) {
        this.config = config;
        this.random = new Random();
        this.tradeRepository = tradeRepository;
    }

    @Override
    public void initialize(OrderManager orderManager, MatchingEngine matchingEngine) {
        this.orderManager = orderManager;
        this.matchingEngine = matchingEngine;
        LOGGER.log(Level.INFO, "TrendFollower initialized for symbols: {0}", config.symbols());
    }

    @Override
    public void execute(String symbol) {
        try {
            var bookOpt = matchingEngine.getOrderBook(symbol);

            if (bookOpt.isEmpty()) return;

            OrderBook book = bookOpt.get();

            // Analyze recent trades to determine trend
            Trend trend = analyzeTrend(symbol);

            if (trend == Trend.NEUTRAL) return; // No clear trend, skip

            BigDecimal referencePrice = book.getLastTradePrice();
            if (referencePrice == null) {
                BigDecimal bid = book.getBestBid();
                BigDecimal ask = book.getBestAsk();
                if (bid != null && ask != null) referencePrice = bid.add(ask).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                else referencePrice = PriceGenerator.getDefaultPrice(symbol);
            }

            // Follow the trend
            boolean isBuy = (trend == Trend.UPWARD);

            BigDecimal orderPrice;
            if (isBuy) orderPrice = referencePrice.multiply(BigDecimal.valueOf(1.001)).setScale(2, RoundingMode.HALF_UP); // Aggressive buy (slightly above market)
            else orderPrice = referencePrice.multiply(BigDecimal.valueOf(0.999)).setScale(2, RoundingMode.HALF_UP); // Aggressive sell (slightly below market)
            
            int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);

            String side = isBuy ? "BUY" : "SELL";
            LOGGER.log(Level.FINE, "TrendFollower placing {0}: {1} x {2} @ {3} (trend: {4})",
                    new Object[]{side, symbol, quantity, orderPrice, trend});

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing TrendFollower for " + symbol, e);
        }
    }

    private Trend analyzeTrend(String symbol) {
        try {
            // Get recent trades (last 5 minutes)
            Instant start = Instant.now().minus(5, ChronoUnit.MINUTES);
            Instant end = Instant.now();

            List<Trade> recentTrades = tradeRepository.findBySymbol(symbol, start, end);

            if (recentTrades.size() < 3) return Trend.NEUTRAL;

            // Calculate a simple trend: compare first vs last trade prices
            BigDecimal firstPrice = recentTrades.get(0).getPrice();
            BigDecimal lastPrice = recentTrades.get(recentTrades.size() - 1).getPrice();

            BigDecimal priceChange = lastPrice.subtract(firstPrice);
            BigDecimal percentChange = priceChange.divide(firstPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            // Trend threshold: ±0.1%
            if (percentChange.compareTo(new BigDecimal("0.1")) > 0) return Trend.UPWARD;
            else if (percentChange.compareTo(new BigDecimal("-0.1")) < 0) return Trend.DOWNWARD;
            else return Trend.NEUTRAL;


        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error analyzing trend for " + symbol, e);
            return Trend.NEUTRAL;
        }
    }

    @Override
    public String getStrategyName() {
        return "TREND_FOLLOWER";
    }

    public OrderManager getOrderManager() {
        return orderManager;
    }

    public void setOrderManager(OrderManager orderManager) {
        this.orderManager = orderManager;
    }

    public TradeRepository getTradeRepository() {
        return tradeRepository;
    }

    public void setTradeRepository(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    private enum Trend {
        UPWARD, DOWNWARD, NEUTRAL
    }
}