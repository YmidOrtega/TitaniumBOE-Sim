package com.boe.simulator.client.connection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConnectionManager {
    private static final Logger LOGGER = Logger.getLogger(ConnectionManager.class.getName());
    
    private final int maxAttempts;
    private final long initialDelaySeconds;
    private final double backoffMultiplier;
    private final long maxDelaySeconds;
    
    private final AtomicInteger attemptCount;
    private volatile boolean reconnecting;
    
    private ReconnectionListener reconnectionListener;
    
    public ConnectionManager(int maxAttempts, long initialDelaySeconds) {
        this(maxAttempts, initialDelaySeconds, 2.0, 60);
    }
    
    public ConnectionManager(int maxAttempts, long initialDelaySeconds, double backoffMultiplier, long maxDelaySeconds) {
        this.maxAttempts = maxAttempts;
        this.initialDelaySeconds = initialDelaySeconds;
        this.backoffMultiplier = backoffMultiplier;
        this.maxDelaySeconds = maxDelaySeconds;
        this.attemptCount = new AtomicInteger(0);
        this.reconnecting = false;
    }
 
    public CompletableFuture<Void> attemptReconnect(ReconnectTask reconnectTask) {
        return CompletableFuture.runAsync(() -> {
            reconnecting = true;
            attemptCount.set(0);
            
            while (reconnecting && maxAttempts >= attemptCount.get()) {
                int attempt = attemptCount.incrementAndGet();
                
                LOGGER.log(Level.INFO, "Reconnection attempt {0}/{1}", new Object[]{attempt, maxAttempts});
                
                if (reconnectionListener != null) reconnectionListener.onReconnecting(attempt, maxAttempts);
                
                
                try {
                    // Execute reconnect task
                    reconnectTask.execute();
                    
                    // Success!
                    LOGGER.log(Level.INFO, "Reconnection successful on attempt {0}", attempt);
                    
                    if (reconnectionListener != null) reconnectionListener.onReconnected(attempt);
                    
                    reconnecting = false;
                    attemptCount.set(0);
                    return;
                    
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Reconnection attempt {0} failed: {1}", new Object[]{attempt, e.getMessage()});
                    
                    if (reconnectionListener != null) reconnectionListener.onReconnectFailed(attempt, e);
                    
                    // Calculate backoff delay
                    if (attempt < maxAttempts) {
                        long delay = calculateBackoffDelay(attempt);
                        
                        LOGGER.log(Level.INFO, "Waiting {0}s before next attempt...", delay);
                        
                        try {
                            Thread.sleep(delay * 1000);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            reconnecting = false;
                            throw new RuntimeException("Reconnection interrupted", ie);
                        }
                    }
                }
            }
            
            // All attempts failed
            reconnecting = false;
            LOGGER.log(Level.SEVERE, "All reconnection attempts exhausted ({0})", maxAttempts);
            
            if (reconnectionListener != null) reconnectionListener.onReconnectExhausted();
            
            throw new RuntimeException("Reconnection failed after " + maxAttempts + " attempts");
        });
    }
 
    private long calculateBackoffDelay(int attempt) {
        long delay = (long) (initialDelaySeconds * Math.pow(backoffMultiplier, attempt - 1));
        return Math.min(delay, maxDelaySeconds);
    }

    public void cancelReconnection() {
        reconnecting = false;
        LOGGER.info("Reconnection cancelled");
    }

    public void reset() {
        attemptCount.set(0);
        reconnecting = false;
    }

    public void setReconnectionListener(ReconnectionListener listener) {
        this.reconnectionListener = listener;
    }

    public boolean isReconnecting() {
        return reconnecting;
    }

    public int getAttemptCount() {
        return attemptCount.get();
    }

    @FunctionalInterface
    public interface ReconnectTask {
        void execute() throws Exception;
    }
 
    public interface ReconnectionListener {
        default void onReconnecting(int attempt, int maxAttempts) {}
        default void onReconnected(int successfulAttempt) {}
        default void onReconnectFailed(int attempt, Exception error) {}
        default void onReconnectExhausted() {}
    }
}
