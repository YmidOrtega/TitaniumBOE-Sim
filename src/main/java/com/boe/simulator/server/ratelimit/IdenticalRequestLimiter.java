package com.boe.simulator.server.ratelimit;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    public boolean tryAcquire(String key) {
        return tryAcquireAll(List.of(key));
    }

    /** Acquires every key, or none of them when any key is over its limit. */
    public synchronized boolean tryAcquireAll(Collection<String> keys) {
        long now = nanoClock.getAsLong();
        recent.values().forEach(times -> {
            while (!times.isEmpty() && now - times.peekFirst() >= windowNanos) times.pollFirst();
        });
        recent.values().removeIf(ArrayDeque::isEmpty);

        Set<String> distinct = new LinkedHashSet<>(keys);
        for (String key : distinct) {
            ArrayDeque<Long> times = recent.get(key);
            if (times != null && times.size() >= maxPerWindow) return false;
        }
        for (String key : distinct) recent.computeIfAbsent(key, k -> new ArrayDeque<>()).addLast(now);
        return true;
    }
}
