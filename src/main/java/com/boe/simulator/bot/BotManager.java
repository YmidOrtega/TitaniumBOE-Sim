package com.boe.simulator.bot;

import com.boe.simulator.bot.strategy.TradingStrategy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BotManager {
    private static final Logger LOGGER = Logger.getLogger(BotManager.class.getName());

    private final Map<String, Bot> bots;
    private volatile boolean running;

    public BotManager() {
        this.bots = new ConcurrentHashMap<>();
        this.running = false;
    }

    // Register a new bot
    public void registerBot(BotConfig config, TradingStrategy strategy) {
        if (bots.containsKey(config.botId())) {
            LOGGER.warning("Bot already registered: " + config.botId());
            return;
        }

        Bot bot = new Bot(config, strategy);
        bots.put(config.botId(), bot);

        LOGGER.info("Bot registered: " + config.botId() + " (" + strategy.getStrategyName() + ")");

        // Auto-start if manager is running
        if (running && config.enabled()) bot.start();
    }

    // Start all bots
    public void startAll() {
        if (running) {
            LOGGER.warning("BotManager is already running");
            return;
        }

        running = true;

        for (Bot bot : bots.values()) {
            if (bot.getConfig().enabled()) bot.start();
        }

        LOGGER.info("BotManager started with " + bots.size() + " bots");
    }

    // Stop all bots
    public void stopAll() {
        if (!running) return;

        running = false;

        for (Bot bot : bots.values()) bot.stop();

        LOGGER.info("BotManager stopped");
    }

    // Shutdown and cleanup all resources
    public void shutdown() {
        stopAll();

        for (Bot bot : bots.values()) bot.shutdown();

        bots.clear();
        LOGGER.info("BotManager shutdown complete");
    }

    // Start a specific bot
    public boolean startBot(String botId) {
        Bot bot = bots.get(botId);
        if (bot == null) {
            LOGGER.warning("Bot not found: " + botId);
            return false;
        }

        bot.start();
        return true;
    }

    // Stop a specific bot
    public boolean stopBot(String botId) {
        Bot bot = bots.get(botId);
        if (bot == null) {
            LOGGER.warning("Bot not found: " + botId);
            return false;
        }

        bot.stop();
        return true;
    }

    // Remove a bot
    public boolean removeBot(String botId) {
        Bot bot = bots.remove(botId);
        if (bot == null) return false;

        bot.shutdown();
        LOGGER.info("Bot removed: " + botId);
        return true;
    }

    // Get a specific bot
    public Bot getBot(String botId) {
        return bots.get(botId);
    }

    // Get all bots
    public Collection<Bot> getAllBots() {
        return new ArrayList<>(bots.values());
    }

    // Get running bots count
    public int getRunningBotsCount() {
        return (int) bots.values().stream()
                .filter(Bot::isRunning)
                .count();
    }

    // Get total statistics from all bots
    public BotManagerStatistics getAggregatedStatistics() {
        long totalOrders = 0;
        long successfulOrders = 0;
        long failedOrders = 0;
        long totalVolume = 0;

        for (Bot bot : bots.values()) {
            BotStatistics stats = bot.getStatistics();
            totalOrders += stats.getTotalOrders();
            successfulOrders += stats.getSuccessfulOrders();
            failedOrders += stats.getFailedOrders();
            totalVolume += stats.getTotalVolume();
        }

        return new BotManagerStatistics(
                bots.size(),
                getRunningBotsCount(),
                totalOrders,
                successfulOrders,
                failedOrders,
                totalVolume
        );
    }

    // Print status of all bots
    public void printStatus() {
        LOGGER.info("========== Bot Manager Status ==========");
        LOGGER.log(Level.INFO, "Total Bots: {0}", bots.size());
        LOGGER.log(Level.INFO, "Running: {0}", getRunningBotsCount());
        LOGGER.info("----------------------------------------");

        for (Bot bot : bots.values()) {
            String status = bot.isRunning() ? "RUNNING" : "STOPPED";
            LOGGER.log(Level.INFO, "[{0}] {1} - {2}",
                    new Object[]{status, bot.getBotId(), bot.getStatistics()});
        }

        BotManagerStatistics aggStats = getAggregatedStatistics();
        LOGGER.info("----------------------------------------");
        LOGGER.log(Level.INFO, "Aggregated: orders={0}, success={1}, volume={2}",
                new Object[]{aggStats.totalOrders(), aggStats.successfulOrders(), aggStats.totalVolume()});
        LOGGER.info("========================================");
    }

    public boolean isRunning() {
        return running;
    }

    // Statistics record
    public record BotManagerStatistics(
            int totalBots,
            int runningBots,
            long totalOrders,
            long successfulOrders,
            long failedOrders,
            long totalVolume
    ) {}
}