package com.boe.simulator.server.ratelimit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;

/**
 * Port and Symbol Order Rate Threshold port attributes (p.221): the first non-session message opens a
 * one-second window; above the threshold, new orders in that window are rejected.
 */
public final class OrderRateThreshold {
    private static final long WINDOW_NANOS = TimeUnit.SECONDS.toNanos(1);

    private final int portLimit;
    private final int symbolLimit;
    private final LongSupplier nanoClock;
    private final Map<String, Integer> symbolCounts = new HashMap<>();
    private long windowStart;
    private boolean windowOpen;
    private int portCount;

    public OrderRateThreshold(int portLimit, int symbolLimit) {
        this(portLimit, symbolLimit, System::nanoTime);
    }

    OrderRateThreshold(int portLimit, int symbolLimit, LongSupplier nanoClock) {
        this.portLimit = portLimit;
        this.symbolLimit = Math.min(symbolLimit, portLimit);
        this.nanoClock = nanoClock;
    }

    /** Counts one message; true when the port or the symbol is above its threshold in this window. */
    public synchronized boolean exceeded(String symbol) {
        long now = nanoClock.getAsLong();
        if (!windowOpen || now - windowStart >= WINDOW_NANOS) {
            windowOpen = true;
            windowStart = now;
            portCount = 0;
            symbolCounts.clear();
        }
        portCount++;
        int symbolCount = symbol != null ? symbolCounts.merge(symbol, 1, Integer::sum) : 0;
        return portCount > portLimit || symbolCount > symbolLimit;
    }
}
