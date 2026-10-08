package com.boe.simulator.server.ratelimit;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.LongSupplier;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RateLimiter {
    private static final Logger LOGGER = Logger.getLogger(RateLimiter.class.getName());

    private final ConcurrentHashMap<Integer, TokenBucket> buckets;
    private final int permitsPerSecond;
    private final LongSupplier nanoClock;

    public RateLimiter(int permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime);
    }

    RateLimiter(int permitsPerSecond, LongSupplier nanoClock) {
        if (permitsPerSecond < 1) throw new IllegalArgumentException("Rate limit must be at least 1");
        this.buckets = new ConcurrentHashMap<>();
        this.permitsPerSecond = permitsPerSecond;
        this.nanoClock = nanoClock;
    }

    public void acquire(int connectionId) {
        long waitNanos = reserve(connectionId);
        if (waitNanos <= 0) return;

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.log(Level.FINE, "[Session {0}] Rate limit reached - pausing reads for {1}μs",
                    new Object[]{connectionId, TimeUnit.NANOSECONDS.toMicros(waitNanos)});
        }
        try {
            TimeUnit.NANOSECONDS.sleep(waitNanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    long reserve(int connectionId) {
        return buckets.computeIfAbsent(connectionId, k -> new TokenBucket(permitsPerSecond, nanoClock.getAsLong()))
                .reserve(nanoClock.getAsLong());
    }

    public void clearConnection(int connectionId) {
        buckets.remove(connectionId);
    }

    private static final class TokenBucket {
        private final double capacity;
        private final double permitsPerNano;
        private double tokens;
        private long lastRefillNanos;

        TokenBucket(int permitsPerSecond, long nowNanos) {
            this.capacity = permitsPerSecond;
            this.permitsPerNano = permitsPerSecond / 1_000_000_000.0;
            this.tokens = permitsPerSecond;
            this.lastRefillNanos = nowNanos;
        }

        synchronized long reserve(long nowNanos) {
            tokens = Math.min(capacity, tokens + (nowNanos - lastRefillNanos) * permitsPerNano);
            lastRefillNanos = nowNanos;

            tokens -= 1;
            return tokens >= 0 ? 0 : (long) Math.ceil(-tokens / permitsPerNano);
        }
    }
}
