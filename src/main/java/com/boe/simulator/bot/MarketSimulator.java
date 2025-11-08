package com.boe.simulator.bot;

import com.boe.simulator.bot.strategy.MarketMakerStrategy;
import com.boe.simulator.bot.strategy.RandomTraderStrategy;
import com.boe.simulator.bot.strategy.TrendFollowerStrategy;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.order.OrderManager;

import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class MarketSimulator {
    private static final Logger LOGGER = Logger.getLogger(MarketSimulator.class.getName());

    private final OrderManager orderManager;
    private final MatchingEngine matchingEngine;
    private final TradeRepository tradeRepository;
    private final BotManager botManager;
    private final ScheduledExecutorService statusScheduler;

    private volatile boolean running;

    public MarketSimulator(
            OrderManager orderManager,
            MatchingEngine matchingEngine,
            TradeRepository tradeRepository
    ) {
        this.orderManager = orderManager;
        this.matchingEngine = matchingEngine;
        this.tradeRepository = tradeRepository;
        this.botManager = new BotManager();
        this.statusScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("MarketSimulator-Status");
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    // Initialize with default bots
    public void initializeDefaultBots() {
        Set<String> primarySymbols = Set.of("AAPL", "MSFT", "GOOGL");
        Set<String> allSymbols = Set.of("AAPL", "MSFT", "GOOGL", "AMZN", "META");

        // Bot 1: Market Maker (provides liquidity)
        BotConfig mmConfig = BotConfig.marketMaker(
                "MM-001",
                primarySymbols,
                "MARKET_MAKER"
        );
        MarketMakerStrategy mmStrategy = new MarketMakerStrategy(mmConfig);
        mmStrategy.initialize(orderManager, matchingEngine);
        botManager.registerBot(mmConfig, mmStrategy);

        // Bot 2: Random Trader (simulates retail traders)
        BotConfig rtConfig = BotConfig.randomTrader(
                "TRADER-001",
                allSymbols,
                "BOT_TRADER"
        );
        RandomTraderStrategy rtStrategy = new RandomTraderStrategy(rtConfig);
        rtStrategy.initialize(orderManager, matchingEngine);
        botManager.registerBot(rtConfig, rtStrategy);

        // Bot 3: Trend Follower (follows market trends)
        BotConfig tfConfig = BotConfig.trendFollower(
                "TREND-001",
                primarySymbols,
                "BOT_TREND"
        );
        TrendFollowerStrategy tfStrategy = new TrendFollowerStrategy(tfConfig, tradeRepository);
        tfStrategy.initialize(orderManager, matchingEngine);
        botManager.registerBot(tfConfig, tfStrategy);

        LOGGER.info("Default bots initialized");
    }

    // Start the market simulator
    public void start() {
        if (running) {
            LOGGER.warning("MarketSimulator is already running");
            return;
        }

        running = true;

        // Start all bots
        botManager.startAll();

        // Schedule periodic status reporting (every 30 seconds)
        statusScheduler.scheduleAtFixedRate(
                this::printStatus,
                30,
                30,
                TimeUnit.SECONDS
        );

        LOGGER.info("✓ MarketSimulator started");
    }

    // Stop the market simulator
    public void stop() {
        if (!running) return;

        running = false;

        // Stop all bots
        botManager.stopAll();

        // Stop status reporting
        statusScheduler.shutdown();

        LOGGER.info("✓ MarketSimulator stopped");
    }

    // Shutdown and clean up all resources
    public void shutdown() {
        stop();

        botManager.shutdown();

        try {
            if (!statusScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                statusScheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            statusScheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }

        LOGGER.info("✓ MarketSimulator shutdown complete");
    }

    // Add a custom bot
    public void addBot(BotConfig config, com.boe.simulator.bot.strategy.TradingStrategy strategy) {
        strategy.initialize(orderManager, matchingEngine);
        botManager.registerBot(config, strategy);

        if (running && config.enabled()) botManager.startBot(config.botId());
    }

    // Remove a bot
    public boolean removeBot(String botId) {
        return botManager.removeBot(botId);
    }

    // Get bot manager for direct access
    public BotManager getBotManager() {
        return botManager;
    }

    // Print current status
    public void printStatus() {
        botManager.printStatus();
    }

    // Get simulator statistics
    public SimulatorStatistics getStatistics() {
        var aggStats = botManager.getAggregatedStatistics();

        return new SimulatorStatistics(
                running,
                aggStats.totalBots(),
                aggStats.runningBots(),
                aggStats.totalOrders(),
                aggStats.successfulOrders(),
                aggStats.failedOrders(),
                aggStats.totalVolume()
        );
    }

    public boolean isRunning() {
        return running;
    }

    // Statistics record
    public record SimulatorStatistics(
            boolean running,
            int totalBots,
            int runningBots,
            long totalOrders,
            long successfulOrders,
            long failedOrders,
            long totalVolume
    ) {}
}