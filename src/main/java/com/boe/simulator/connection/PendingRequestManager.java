package com.boe.simulator.connection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
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
                    LOGGER.warning("Request timeout for " + type);
                    pendingRequests.remove(type);
                    return null;
                });

        LOGGER.fine("Registered pending request: " + type);
        return future;
    }

    @SuppressWarnings("unchecked")
    public <T> boolean completeRequest(RequestType type, T response) {
        CompletableFuture<Object> future = pendingRequests.remove(type);

        if (future == null) {
            LOGGER.warning("No pending request found for: " + type);
            return false;
        }

        future.complete(response);
        LOGGER.fine("Completed request: " + type);
        return true;
    }

    public boolean failRequest(RequestType type, Throwable exception) {
        CompletableFuture<Object> future = pendingRequests.remove(type);

        if (future == null) {
            LOGGER.warning("No pending request found to fail: " + type);
            return false;
        }

        future.completeExceptionally(exception);
        LOGGER.fine("Failed request: " + type);
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