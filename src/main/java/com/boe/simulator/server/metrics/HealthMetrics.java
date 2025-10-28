package com.boe.simulator.server.metrics;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

public class HealthMetrics {
    
    private final Instant startTime;
    private final AtomicLong totalBytesReceived;
    private final AtomicLong totalBytesSent;
    private final AtomicLong peakActiveConnections;
    
    public HealthMetrics() {
        this.startTime = Instant.now();
        this.totalBytesReceived = new AtomicLong(0);
        this.totalBytesSent = new AtomicLong(0);
        this.peakActiveConnections = new AtomicLong(0);
    }
    
    public void recordBytesReceived(long bytes) {
        totalBytesReceived.addAndGet(bytes);
    }
    
    public void recordBytesSent(long bytes) {
        totalBytesSent.addAndGet(bytes);
    }
    
    public void updatePeakConnections(int current) {
        long peak = peakActiveConnections.get();
        if (current > peak) peakActiveConnections.compareAndSet(peak, current);
    }
    
    public long getUptimeSeconds() {
        return java.time.Duration.between(startTime, Instant.now()).getSeconds();
    }
    
    public long getTotalBytesReceived() {
        return totalBytesReceived.get();
    }
    
    public long getTotalBytesSent() {
        return totalBytesSent.get();
    }
    
    public long getPeakActiveConnections() {
        return peakActiveConnections.get();
    }
    
    public String getHealthSummary() {
        return String.format(
            "Health: Uptime=%ds, BytesRx=%d, BytesTx=%d, PeakConnections=%d",
            getUptimeSeconds(),
            getTotalBytesReceived(),
            getTotalBytesSent(),
            getPeakActiveConnections()
        );
    }
}
