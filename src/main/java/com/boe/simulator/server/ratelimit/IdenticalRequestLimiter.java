package com.boe.simulator.server.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.function.LongSupplier;

public final class IdenticalRequestLimiter {
    private final int maxPerWindow;
    private final long windowNanos;
    private final LongSupplier nanoClock;
    private final Map<String, ArrayDeque<Long>> recent = new HashMap<>();

    public IdenticalRequestLimiter(int maxPerWindow, Duration window) {
        this(maxPerWindow, window, System::nanoTime);
    }

    IdenticalRequestLimiter(int maxPerWindow, Duration window, LongSupplier nanoClock) {
        this.maxPerWindow = maxPerWindow;
        this.windowNanos = window.toNanos();
        this.nanoClock = nanoClock;
    }

    public synchronized boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        recent.values().forEach(times -> {
            while (!times.isEmpty() && now - times.peekFirst() >= windowNanos) times.pollFirst();
        });
        recent.values().removeIf(ArrayDeque::isEmpty);

        ArrayDeque<Long> times = recent.computeIfAbsent(key, k -> new ArrayDeque<>());
        if (times.size() >= maxPerWindow) return false;
        times.addLast(now);
        return true;
    }
}
