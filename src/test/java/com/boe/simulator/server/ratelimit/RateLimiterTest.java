package com.boe.simulator.server.ratelimit;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private static final int PERMITS_PER_SECOND = 3;
    private static final long NANOS_PER_PERMIT = 1_000_000_000L / PERMITS_PER_SECOND;

    private final AtomicLong clock = new AtomicLong();
    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(PERMITS_PER_SECOND, clock::get);
    }

    @Test
    void reserve_withinBurst_doesNotWait() {
        assertEquals(0, rateLimiter.reserve(1));
        assertEquals(0, rateLimiter.reserve(1));
        assertEquals(0, rateLimiter.reserve(1));
    }

    @Test
    void reserve_beyondBurst_waitsInsteadOfRejecting() {
        drain(2);

        long wait = rateLimiter.reserve(2);

        assertTrue(wait > 0, "Message beyond the burst must wait, not be dropped");
        assertEquals(NANOS_PER_PERMIT, wait, 1);
    }

    @Test
    void reserve_consecutiveOverflow_queuesWaitsInOrder() {
        drain(3);

        long first = rateLimiter.reserve(3);
        long second = rateLimiter.reserve(3);

        assertEquals(NANOS_PER_PERMIT, first, 1);
        assertEquals(2 * NANOS_PER_PERMIT, second, 1);
    }

    @Test
    void reserve_afterRefill_doesNotWait() {
        drain(4);

        clock.addAndGet(NANOS_PER_PERMIT + 1);

        assertEquals(0, rateLimiter.reserve(4));
    }

    @Test
    void reserve_refillIsCappedAtBurstSize() {
        clock.addAndGet(60_000_000_000L); // idle for a minute
        drain(5);

        assertTrue(rateLimiter.reserve(5) > 0, "Idle time must not accumulate more than one burst");
    }

    @Test
    void reserve_multipleConnections_areIndependent() {
        drain(6);

        assertEquals(0, rateLimiter.reserve(7), "Connection 7 has its own bucket");
    }

    @Test
    void clearConnection_resetsBucket() {
        drain(8);

        rateLimiter.clearConnection(8);

        assertEquals(0, rateLimiter.reserve(8));
    }

    @Test
    void acquire_beyondBurst_blocksForAboutOnePermit() {
        RateLimiter realClock = new RateLimiter(100); // 10ms per permit
        for (int i = 0; i < 100; i++) realClock.acquire(9);

        long t0 = System.nanoTime();
        realClock.acquire(9);
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertTrue(elapsedMs >= 5, "acquire must block once the burst is used, took " + elapsedMs + "ms");
    }

    @Test
    void constructor_rejectsNonPositiveRate() {
        assertThrows(IllegalArgumentException.class, () -> new RateLimiter(0));
    }

    private void drain(int connectionId) {
        for (int i = 0; i < PERMITS_PER_SECOND; i++) assertEquals(0, rateLimiter.reserve(connectionId));
    }
}
