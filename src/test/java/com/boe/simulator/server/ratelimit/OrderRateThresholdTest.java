package com.boe.simulator.server.ratelimit;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;

class OrderRateThresholdTest {

    private final AtomicLong clock = new AtomicLong();

    @Test
    void portThreshold_isCountedFromTheFirstMessageOfTheWindow() {
        OrderRateThreshold threshold = new OrderRateThreshold(3, 3, clock::get);

        assertFalse(threshold.exceeded(null));
        clock.addAndGet(900_000_000L);
        assertFalse(threshold.exceeded(null));
        assertFalse(threshold.exceeded(null));
        assertTrue(threshold.exceeded(null), "4th message within one second of the first");

        clock.addAndGet(100_000_000L);
        assertFalse(threshold.exceeded(null), "A new window starts one second after the first message");
    }

    @Test
    void symbolThreshold_isCountedPerSymbol_andCappedByThePortThreshold() {
        OrderRateThreshold threshold = new OrderRateThreshold(5, 2, clock::get);

        assertFalse(threshold.exceeded("AAPL"));
        assertFalse(threshold.exceeded("AAPL"));
        assertTrue(threshold.exceeded("AAPL"));
        assertFalse(threshold.exceeded("MSFT"), "Other symbols have their own count");

        OrderRateThreshold capped = new OrderRateThreshold(1, 10, clock::get);
        capped.exceeded("AAPL");
        assertTrue(capped.exceeded("AAPL"));
    }
}
