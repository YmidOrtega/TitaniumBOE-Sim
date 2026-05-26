package com.boe.simulator.bot.strategy;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

import com.boe.simulator.bot.BotConfig;
import com.boe.simulator.bot.util.PriceGenerator;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.order.OrderManager;

public final class TrendFollowerStrategy implements TradingStrategy {
    private static final Logger LOGGER = Logger.getLogger(TrendFollowerStrategy.class.getName());

    private static final AtomicLong SEQ = new AtomicLong(0);

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

            // First: check order book imbalance from the top levels
            Trend imbalanceTrend = analyzeImbalance(book);

            // Fall back to trade history if book is neutral
            Trend trend = (imbalanceTrend != Trend.NEUTRAL) ? imbalanceTrend : analyzeTradeTrend(symbol);

            BigDecimal bestBid = book.getBestBid();
            BigDecimal bestAsk = book.getBestAsk();
            BigDecimal referencePrice = getReferencePrice(book, symbol);

            if (trend == Trend.UPWARD) {
                // Aggressive buy: price above best ask
                if (bestAsk != null) {
                    BigDecimal aggressivePrice = bestAsk.multiply(BigDecimal.valueOf(1.001)).setScale(2, RoundingMode.HALF_UP);
                    int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
                    submitOrder(symbol, (byte) 1, quantity, aggressivePrice, (byte) 0);
                } else {
                    // No ask in book; place passive buy
                    placePassiveOrder(symbol, (byte) 1, referencePrice);
                }
            } else if (trend == Trend.DOWNWARD) {
                // Aggressive sell: price below best bid
                if (bestBid != null) {
                    BigDecimal aggressivePrice = bestBid.multiply(BigDecimal.valueOf(0.999)).setScale(2, RoundingMode.HALF_UP);
                    int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
                    submitOrder(symbol, (byte) 2, quantity, aggressivePrice, (byte) 0);
                } else {
                    // No bid in book; place passive sell
                    placePassiveOrder(symbol, (byte) 2, referencePrice);
                }
            } else {
                // NEUTRAL: always trade — place a passive order at mid ± small variation
                boolean isBuy = random.nextBoolean();
                placePassiveOrder(symbol, isBuy ? (byte) 1 : (byte) 2, referencePrice);
            }

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error executing TrendFollower for %s".formatted(symbol), e);
        }
    }

    // Analyze order book imbalance using total bid vs ask quantity
    private Trend analyzeImbalance(OrderBook book) {
        int bidQty = book.getTotalBidQuantity();
        int askQty = book.getTotalAskQuantity();

        if (bidQty == 0 && askQty == 0) return Trend.NEUTRAL;

        // Need at least some presence on both sides to determine imbalance
        if (askQty == 0) return Trend.UPWARD;
        if (bidQty == 0) return Trend.DOWNWARD;

        // 1.2x imbalance threshold
        if ((double) bidQty > askQty * 1.2) return Trend.UPWARD;
        if ((double) askQty > bidQty * 1.2) return Trend.DOWNWARD;

        return Trend.NEUTRAL;
    }

    // Analyze recent trade history to determine price trend
    private Trend analyzeTradeTrend(String symbol) {
        try {
            Instant start = Instant.now().minus(5, ChronoUnit.MINUTES);
            Instant end = Instant.now();

            List<Trade> recentTrades = tradeRepository.findBySymbol(symbol, start, end);

            if (recentTrades.size() < 3) return Trend.NEUTRAL;

            BigDecimal firstPrice = recentTrades.get(0).getPrice();
            BigDecimal lastPrice = recentTrades.get(recentTrades.size() - 1).getPrice();

            BigDecimal priceChange = lastPrice.subtract(firstPrice);
            BigDecimal percentChange = priceChange.divide(firstPrice, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));

            if (percentChange.compareTo(new BigDecimal("0.1")) > 0) return Trend.UPWARD;
            else if (percentChange.compareTo(new BigDecimal("-0.1")) < 0) return Trend.DOWNWARD;
            else return Trend.NEUTRAL;

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error analyzing trade trend for %s".formatted(symbol), e);
            return Trend.NEUTRAL;
        }
    }

    private BigDecimal getReferencePrice(OrderBook book, String symbol) {
        BigDecimal lastTradePrice = book.getLastTradePrice();
        if (lastTradePrice != null) return lastTradePrice;

        BigDecimal bid = book.getBestBid();
        BigDecimal ask = book.getBestAsk();
        if (bid != null && ask != null) return bid.add(ask).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (bid != null) return bid;
        if (ask != null) return ask;

        return PriceGenerator.getDefaultPrice(symbol);
    }

    // Place a passive limit order at reference ± small variation
    private void placePassiveOrder(String symbol, byte side, BigDecimal referencePrice) {
        BigDecimal variation = referencePrice.multiply(config.priceVariation())
                .multiply(BigDecimal.valueOf(random.nextDouble(-1, 1)));
        BigDecimal orderPrice = referencePrice.add(variation).setScale(2, RoundingMode.HALF_UP);
        int quantity = random.nextInt(config.minQuantity(), config.maxQuantity() + 1);
        submitOrder(symbol, side, quantity, orderPrice, (byte) 0);
    }

    private void submitOrder(String symbol, byte side, int qty, BigDecimal price, byte timeInForce) {
        if (orderManager == null) return;
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(String.format("TF%018d", SEQ.incrementAndGet()));
        msg.setSide(side);
        msg.setOrderQty(qty);
        msg.setPrice(price);
        msg.setSymbol(symbol);
        msg.setOrdType((byte) 2);           // Limit
        msg.setTimeInForce(timeInForce);
        msg.setCapacity((byte) 'C');        // Customer
        orderManager.processNewOrder(msg, "BOT-TREND");
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
