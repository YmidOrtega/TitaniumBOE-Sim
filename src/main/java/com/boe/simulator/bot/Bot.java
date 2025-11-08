package com.boe.simulator.bot;

import com.boe.simulator.bot.strategy.TradingStrategy;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class Bot {
    private static final Logger LOGGER = Logger.getLogger(Bot.class.getName());

    private final BotConfig config;
    private final TradingStrategy strategy;
    private final BotStatistics statistics;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> scheduledTask;
    private volatile boolean running;

    public Bot(BotConfig config, TradingStrategy strategy) {
        this.config = config;
        this.strategy = strategy;
        this.statistics = new BotStatistics(config.botId());
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r);
            t.setName("Bot-" + config.botId());
            t.setDaemon(true);
            return t;
        });
        this.running = false;
    }

    public void start() {
        if (running) {
            LOGGER.warning("Bot " + config.botId() + " is already running");
            return;
        }

        running = true;
        statistics.start();

        // Schedule periodic execution
        scheduledTask = scheduler.scheduleAtFixedRate(
                this::executeTradingCycle,
                0,
                config.actionInterval().toMillis(),
                TimeUnit.MILLISECONDS
        );

        LOGGER.info("Bot started: " + config.botId() + " (" + strategy.getStrategyName() + ")");
    }

    public void stop() {
        if (!running) return;

        running = false;
        statistics.stop();

        if (scheduledTask != null) scheduledTask.cancel(false);

        strategy.cleanup();

        LOGGER.info("Bot stopped: " + config.botId());
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();

        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private void executeTradingCycle() {
        if (!running || !config.enabled()) return;

        try {
            for (String symbol : config.symbols()) {
                statistics.recordOrderSubmitted();
                strategy.execute(symbol);
                statistics.recordOrderSuccess(50); // Placeholder quantity
            }
        } catch (Exception e) {
            statistics.recordOrderFailed();
            LOGGER.log(Level.WARNING, "Error in bot " + config.botId() + " trading cycle", e);
        }
    }

    // Getters
    public String getBotId() { return config.botId(); }
    public BotConfig getConfig() { return config; }
    public BotStatistics getStatistics() { return statistics; }
    public boolean isRunning() { return running; }
}