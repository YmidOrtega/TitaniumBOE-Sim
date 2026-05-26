package com.boe.simulator.server.error;

import java.io.IOException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
;

public class ErrorHandler {
    private static final Logger LOGGER = Logger.getLogger(ErrorHandler.class.getName());
    
    // Error statistics per connection
    private final ConcurrentHashMap<Integer, ConnectionErrorStats> errorStats;
    
    // Global error counters
    private final AtomicInteger totalErrors;
    private final AtomicInteger totalWarnings;
    private final AtomicInteger totalRecoveries;
    
    public ErrorHandler() {
        this.errorStats = new ConcurrentHashMap<>();
        this.totalErrors = new AtomicInteger(0);
        this.totalWarnings = new AtomicInteger(0);
        this.totalRecoveries = new AtomicInteger(0);
    }

    public void handleError(int connectionId, String context, Throwable error) {
        ErrorSeverity severity = classifyError(error);
        
        // Update statistics
        getOrCreateStats(connectionId).recordError(severity);
        
        switch (severity) {
            case CRITICAL -> {
                totalErrors.incrementAndGet();
                logError(connectionId, context, error, Level.SEVERE);
            }
            case RECOVERABLE -> {
                totalWarnings.incrementAndGet();
                logError(connectionId, context, error, Level.WARNING);
                totalRecoveries.incrementAndGet();
            }
            case INFORMATIONAL -> logError(connectionId, context, error, Level.INFO);
        }
    }

    private ErrorSeverity classifyError(Throwable error) {
        return switch (error) {
            case SocketTimeoutException t -> ErrorSeverity.INFORMATIONAL;
            case SocketException e when e.getMessage() != null &&
                    (e.getMessage().contains("Connection reset") || e.getMessage().contains("Broken pipe"))
                    -> ErrorSeverity.INFORMATIONAL;
            case SocketException e -> ErrorSeverity.RECOVERABLE;
            case IOException e when e.getMessage() != null && e.getMessage().contains("End of stream")
                    -> ErrorSeverity.INFORMATIONAL;
            case IOException e -> ErrorSeverity.RECOVERABLE;
            case IllegalArgumentException e -> ErrorSeverity.RECOVERABLE;
            default -> ErrorSeverity.CRITICAL;
        };
    }

    private void logError(int connectionId, String context, Throwable error, Level level) {
        String message = String.format("[Session %d] %s: %s", connectionId, context, error.getMessage());
        
        if (level == Level.SEVERE) LOGGER.log(level, message, error);
        else LOGGER.log(level, message);
    }

    public boolean shouldTerminateConnection(int connectionId) {
        ConnectionErrorStats stats = errorStats.get(connectionId);
        if (stats == null) return false;
        
        // Terminate if more than 10 errors in last minute
        return stats.getErrorCount() > 10;
    }

    private ConnectionErrorStats getOrCreateStats(int connectionId) {
        return errorStats.computeIfAbsent(connectionId, k -> new ConnectionErrorStats());
    }

    public void clearConnectionStats(int connectionId) {
        errorStats.remove(connectionId);
    }
    
    // Statistics getters
    public int getTotalErrors() {
        return totalErrors.get();
    }
    
    public int getTotalWarnings() {
        return totalWarnings.get();
    }
    
    public int getTotalRecoveries() {
        return totalRecoveries.get();
    }

    public enum ErrorSeverity {
        INFORMATIONAL,
        RECOVERABLE,
        CRITICAL
    }

    private static class ConnectionErrorStats {
        private final AtomicInteger errorCount = new AtomicInteger(0);
        
        void recordError(ErrorSeverity severity) {
            if (severity != ErrorSeverity.INFORMATIONAL) errorCount.incrementAndGet();
        }
        
        int getErrorCount() {
            return errorCount.get();
        }
    }
}
