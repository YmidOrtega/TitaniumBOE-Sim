package com.boe.simulator.bot;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

public class BotStatistics {
    private final String botId;
    private final AtomicLong totalOrders;
    private final AtomicLong successfulOrders;
    private final AtomicLong failedOrders;
    private final AtomicLong cancelledOrders;
    private final LongAdder totalVolume;
    private final AtomicLong startTime;
    private volatile boolean running;

    public BotStatistics(String botId) {
        this.botId = botId;
        this.totalOrders = new AtomicLong(0);
        this.successfulOrders = new AtomicLong(0);
        this.failedOrders = new AtomicLong(0);
        this.cancelledOrders = new AtomicLong(0);
        this.totalVolume = new LongAdder();
        this.startTime = new AtomicLong(System.currentTimeMillis());
        this.running = false;
    }

    public void recordOrderSubmitted() {
        totalOrders.incrementAndGet();
    }

    public void recordOrderSuccess(int quantity) {
        successfulOrders.incrementAndGet();
        totalVolume.add(quantity);
    }

    public void recordOrderFailed() {
        failedOrders.incrementAndGet();
    }

    public void recordOrderCancelled() {
        cancelledOrders.incrementAndGet();
    }

    public void start() {
        running = true;
        startTime.set(System.currentTimeMillis());
    }

    public void stop() {
        running = false;
    }

    // Getters
    public String getBotId() { return botId; }
    public long getTotalOrders() { return totalOrders.get(); }
    public long getSuccessfulOrders() { return successfulOrders.get(); }
    public long getFailedOrders() { return failedOrders.get(); }
    public long getCancelledOrders() { return cancelledOrders.get(); }
    public long getTotalVolume() { return totalVolume.sum(); }
    public boolean isRunning() { return running; }
    public long getUptimeMillis() { return System.currentTimeMillis() - startTime.get(); }

    public double getSuccessRate() {
        long total = totalOrders.get();
        return total > 0 ? (double) successfulOrders.get() / total * 100.0 : 0.0;
    }

    @Override
    public String toString() {
        return String.format(
                "BotStats[%s]{orders=%d, success=%d(%.1f%%), failed=%d, volume=%d, uptime=%ds}",
                botId,
                getTotalOrders(),
                getSuccessfulOrders(),
                getSuccessRate(),
                getFailedOrders(),
                getTotalVolume(),
                getUptimeMillis() / 1000
        );
    }
}