package com.boe.simulator.server.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;
    private final int MAX_MESSAGES = 3;
    private final Duration WINDOW_DURATION = Duration.ofMillis(100);

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(MAX_MESSAGES, WINDOW_DURATION);
    }

    @Test
    void allowMessage_whenWithinLimit_returnsTrue() {
        int connectionId = 1;
        assertTrue(rateLimiter.allowMessage(connectionId), "First message should be allowed");
        assertTrue(rateLimiter.allowMessage(connectionId), "Second message should be allowed");
        assertTrue(rateLimiter.allowMessage(connectionId), "Third message should be allowed");
    }

    @Test
    void allowMessage_whenLimitExceeded_returnsFalse() {
        int connectionId = 2;
        rateLimiter.allowMessage(connectionId); // 1
        rateLimiter.allowMessage(connectionId); // 2
        rateLimiter.allowMessage(connectionId); // 3
        assertFalse(rateLimiter.allowMessage(connectionId), "Fourth message should be rejected");
    }

    @Test
    void allowMessage_whenLimitExceededThenWindowResets_returnsTrue() throws InterruptedException {
        int connectionId = 3;
        rateLimiter.allowMessage(connectionId); // 1
        rateLimiter.allowMessage(connectionId); // 2
        rateLimiter.allowMessage(connectionId); // 3
        assertFalse(rateLimiter.allowMessage(connectionId), "Fourth message should be rejected");

        Thread.sleep(WINDOW_DURATION.toMillis() + 10); // Wait for window to reset

        assertTrue(rateLimiter.allowMessage(connectionId), "Message after window reset should be allowed");
    }

    @Test
    void allowMessage_multipleConnections_areIndependent() {
        int connectionId1 = 4;
        int connectionId2 = 5;

        // Fill up connection 1
        rateLimiter.allowMessage(connectionId1);
        rateLimiter.allowMessage(connectionId1);
        rateLimiter.allowMessage(connectionId1);
        assertFalse(rateLimiter.allowMessage(connectionId1), "Connection 1 should be rate limited");

        // Connection 2 should still be allowed
        assertTrue(rateLimiter.allowMessage(connectionId2), "Connection 2 first message should be allowed");
        assertTrue(rateLimiter.allowMessage(connectionId2), "Connection 2 second message should be allowed");
    }

    @Test
    void clearConnection_removesRateLimitForConnection() {
        int connectionId = 6;
        rateLimiter.allowMessage(connectionId); // 1
        rateLimiter.allowMessage(connectionId); // 2
        rateLimiter.allowMessage(connectionId); // 3
        assertFalse(rateLimiter.allowMessage(connectionId), "Should be rate limited initially");

        rateLimiter.clearConnection(connectionId);

        assertTrue(rateLimiter.allowMessage(connectionId), "Should be allowed after clearing connection");
    }

    @Test
    void allowMessage_whenWindowDurationIsZero_behavesCorrectly() {
        RateLimiter zeroWindowRateLimiter = new RateLimiter(MAX_MESSAGES, Duration.ZERO);
        int connectionId = 7;

        // All messages should be allowed as window resets immediately
        assertTrue(zeroWindowRateLimiter.allowMessage(connectionId));
        assertTrue(zeroWindowRateLimiter.allowMessage(connectionId));
        assertTrue(zeroWindowRateLimiter.allowMessage(connectionId));
        assertTrue(zeroWindowRateLimiter.allowMessage(connectionId));
    }
}
