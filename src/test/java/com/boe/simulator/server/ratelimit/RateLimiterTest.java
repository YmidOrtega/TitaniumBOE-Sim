package com.boe.simulator.server.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {

    private RateLimiter rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiter(10, Duration.ofSeconds(1));
    }

    @Test
    void allowMessage_shouldReturnTrue_whenLimitIsNotExceeded() {
        // Act & Assert
        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimiter.allowMessage(1));
        }
    }

    @Test
    void allowMessage_shouldReturnFalse_whenLimitIsExceeded() {
        // Arrange
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowMessage(1);
        }

        // Act & Assert
        assertFalse(rateLimiter.allowMessage(1));
    }

    @Test
    void allowMessage_shouldResetAfterWindow() throws InterruptedException {
        // Arrange
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowMessage(1);
        }
        assertFalse(rateLimiter.allowMessage(1));

        // Act
        Thread.sleep(1100);

        // Assert
        assertTrue(rateLimiter.allowMessage(1));
    }

    @Test
    void clearConnection_shouldResetRateLimitForConnection() {
        // Arrange
        for (int i = 0; i < 10; i++) {
            rateLimiter.allowMessage(1);
        }
        assertFalse(rateLimiter.allowMessage(1));

        // Act
        rateLimiter.clearConnection(1);

        // Assert
        assertTrue(rateLimiter.allowMessage(1));
    }
}