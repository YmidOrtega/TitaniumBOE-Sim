package com.boe.simulator.server.ratelimit;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class IdenticalRequestLimiterTest {

    private final AtomicLong now = new AtomicLong();
    private final IdenticalRequestLimiter limiter = new IdenticalRequestLimiter(10, Duration.ofSeconds(1), now::get);

    @Test
    void eleventhIdenticalRequestInTheWindow_isRefused() {
        for (int i = 0; i < 10; i++) assertTrue(limiter.tryAcquire("K"), "Request " + (i + 1));
        assertFalse(limiter.tryAcquire("K"));
    }

    @Test
    void differentKeys_haveTheirOwnLimit() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("K");
        assertTrue(limiter.tryAcquire("OTHER"));
    }

    @Test
    void requestsOlderThanTheWindow_stopCounting() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("K");
        now.addAndGet(Duration.ofMillis(999).toNanos());
        assertFalse(limiter.tryAcquire("K"));

        now.addAndGet(Duration.ofMillis(1).toNanos());
        assertTrue(limiter.tryAcquire("K"));
    }

    @Test
    void refusedRequests_doNotExtendTheWindow() {
        for (int i = 0; i < 10; i++) limiter.tryAcquire("K");
        now.addAndGet(Duration.ofMillis(500).toNanos());
        assertFalse(limiter.tryAcquire("K"));

        now.addAndGet(Duration.ofMillis(500).toNanos());
        assertTrue(limiter.tryAcquire("K"));
    }

    @Test
    void tryAcquireAll_takesEveryKeyOrNone() {
        IdenticalRequestLimiter once = new IdenticalRequestLimiter(1, Duration.ofMillis(100), now::get);
        assertTrue(once.tryAcquire("F"));

        assertFalse(once.tryAcquireAll(List.of("C", "F")));
        assertTrue(once.tryAcquire("C"), "C was not taken by the refused request");
        assertTrue(once.tryAcquireAll(List.of("S", "S")), "Repeated keys count once");
    }
}
