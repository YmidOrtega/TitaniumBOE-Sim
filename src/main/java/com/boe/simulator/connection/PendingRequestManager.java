package com.boe.simulator.connection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PendingRequestManager {
    private static final Logger LOGGER = Logger.getLogger(PendingRequestManager.class.getName());

    private final ConcurrentHashMap<RequestType, CompletableFuture<Object>> pendingRequests;

    public enum RequestType {
        LOGIN,
        LOGOUT,
        ORDER,
        CANCEL
    }

    public PendingRequestManager() {
        this.pendingRequests = new ConcurrentHashMap<>();
    }

    public <T> CompletableFuture<T> registerRequest(RequestType type) {
        CompletableFuture<T> future = new CompletableFuture<>();

        @SuppressWarnings("unchecked")
        CompletableFuture<Object> objFuture = (CompletableFuture<Object>) (CompletableFuture<?>) future;

        pendingRequests.put(type, objFuture);

        // Set timeout (30 seconds)
        future.orTimeout(30, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    LOGGER.log(Level.WARNING, "Request timeout for {0}", type);
                    pendingRequests.remove(type);
                    return null;
                });

        LOGGER.log(Level.FINE, "Registered pending request: {0}", type);
        return future;
    }

    public <T> boolean completeRequest(RequestType type, T response) {
        CompletableFuture<Object> future = pendingRequests.remove(type);

        if (future == null) {
            LOGGER.log(Level.WARNING, "No pending request found for: {0}", type);
            return false;
        }

        future.complete(response);
        LOGGER.log(Level.FINE, "Completed request: {0}", type);
        return true;
    }

    public boolean failRequest(RequestType type, Throwable exception) {
        CompletableFuture<Object> future = pendingRequests.remove(type);

        if (future == null) {
            LOGGER.log(Level.WARNING, "No pending request found to fail: {0}", type);
            return false;
        }

        future.completeExceptionally(exception);
        LOGGER.log(Level.FINE, "Failed request: {0}", type);
        return true;
    }

    public boolean hasPendingRequest(RequestType type) {
        return pendingRequests.containsKey(type);
    }

    public void clear() {
        pendingRequests.values().forEach(future ->
                future.completeExceptionally(new Exception("Connection closed"))
        );
        pendingRequests.clear();
        LOGGER.info("Cleared all pending requests");
    }

    public int getPendingCount() {
        return pendingRequests.size();
    }
}