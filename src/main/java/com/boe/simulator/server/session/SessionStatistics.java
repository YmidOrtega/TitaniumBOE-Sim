package com.boe.simulator.server.session;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class SessionStatistics {
    
    private final Instant serverStartTime;
    
    // Connection statistics
    private final AtomicInteger totalConnections;
    private final AtomicInteger successfulLogins;
    private final AtomicInteger failedLogins;
    
    // Message statistics
    private final AtomicLong totalMessagesReceived;
    private final AtomicLong totalMessagesSent;
    
    // Heartbeat statistics
    private final AtomicLong totalHeartbeatsReceived;
    private final AtomicLong totalHeartbeatsSent;
    
    public SessionStatistics() {
        this.serverStartTime = Instant.now();
        this.totalConnections = new AtomicInteger(0);
        this.successfulLogins = new AtomicInteger(0);
        this.failedLogins = new AtomicInteger(0);
        this.totalMessagesReceived = new AtomicLong(0);
        this.totalMessagesSent = new AtomicLong(0);
        this.totalHeartbeatsReceived = new AtomicLong(0);
        this.totalHeartbeatsSent = new AtomicLong(0);
    }
    
    // Increment methods
    public void incrementTotalConnections() {
        totalConnections.incrementAndGet();
    }
    
    public void incrementSuccessfulLogins() {
        successfulLogins.incrementAndGet();
    }
    
    public void incrementFailedLogins() {
        failedLogins.incrementAndGet();
    }
    
    public void incrementMessagesReceived() {
        totalMessagesReceived.incrementAndGet();
    }
    
    public void incrementMessagesSent() {
        totalMessagesSent.incrementAndGet();
    }
    
    public void incrementHeartbeatsReceived() {
        totalHeartbeatsReceived.incrementAndGet();
    }
    
    public void incrementHeartbeatsSent() {
        totalHeartbeatsSent.incrementAndGet();
    }
    
    // Getters
    public Instant getServerStartTime() {
        return serverStartTime;
    }
    
    public int getTotalConnections() {
        return totalConnections.get();
    }
    
    public int getSuccessfulLogins() {
        return successfulLogins.get();
    }
    
    public int getFailedLogins() {
        return failedLogins.get();
    }
    
    public long getTotalMessagesReceived() {
        return totalMessagesReceived.get();
    }
    
    public long getTotalMessagesSent() {
        return totalMessagesSent.get();
    }
    
    public long getTotalHeartbeatsReceived() {
        return totalHeartbeatsReceived.get();
    }
    
    public long getTotalHeartbeatsSent() {
        return totalHeartbeatsSent.get();
    }
    
    public long getUptimeSeconds() {
        return java.time.Duration.between(serverStartTime, Instant.now()).getSeconds();
    }
    
    @Override
    public String toString() {
        return "SessionStatistics{" +
                "uptime=" + getUptimeSeconds() + "s" +
                ", totalConnections=" + totalConnections.get() +
                ", successfulLogins=" + successfulLogins.get() +
                ", failedLogins=" + failedLogins.get() +
                ", messagesRx=" + totalMessagesReceived.get() +
                ", messagesTx=" + totalMessagesSent.get() +
                ", heartbeatsRx=" + totalHeartbeatsReceived.get() +
                ", heartbeatsTx=" + totalHeartbeatsSent.get() +
                '}';
    }
}
