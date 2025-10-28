package com.boe.simulator.server.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class RateLimiter {
    private static final Logger LOGGER = Logger.getLogger(RateLimiter.class.getName());
    
    private final ConcurrentHashMap<Integer, ConnectionRateLimit> limits;
    private final int maxMessagesPerWindow;
    private final Duration windowDuration;
    
    public RateLimiter(int maxMessagesPerWindow, Duration windowDuration) {
        this.limits = new ConcurrentHashMap<>();
        this.maxMessagesPerWindow = maxMessagesPerWindow;
        this.windowDuration = windowDuration;
    }

    public boolean allowMessage(int connectionId) {
        ConnectionRateLimit limit = limits.computeIfAbsent(
            connectionId, 
            k -> new ConnectionRateLimit(maxMessagesPerWindow, windowDuration)
        );
        
        boolean allowed = limit.tryAcquire();
        
        if (!allowed) LOGGER.log(Level.WARNING, "[Session {0}] Rate limit exceeded - message rejected", connectionId);
        
        return allowed;
    }

    public void clearConnection(int connectionId) {
        limits.remove(connectionId);
    }

    private static class ConnectionRateLimit {
        private final int maxMessages;
        private final Duration window;
        private int messageCount;
        private Instant windowStart;
        
        ConnectionRateLimit(int maxMessages, Duration window) {
            this.maxMessages = maxMessages;
            this.window = window;
            this.messageCount = 0;
            this.windowStart = Instant.now();
        }
        
        synchronized boolean tryAcquire() {
            Instant now = Instant.now();
            
            // Reset window if expired
            if (Duration.between(windowStart, now).compareTo(window) > 0) {
                messageCount = 0;
                windowStart = now;
            }
            
            // Check limit
            if (messageCount >= maxMessages) return false;
        
            messageCount++;
            return true;
        }
    }
}
