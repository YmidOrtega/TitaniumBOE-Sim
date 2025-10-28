package com.boe.simulator.connection;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;

class PendingRequestManagerTest {

    private PendingRequestManager requestManager;

    @BeforeEach
    void setUp() {
        requestManager = new PendingRequestManager();
    }

    @Test
    void registerRequest_shouldReturnFutureAndStoreIt() {
        // Act
        CompletableFuture<String> future = requestManager.registerRequest(PendingRequestManager.RequestType.LOGIN);

        // Assert
        assertNotNull(future);
        assertTrue(requestManager.hasPendingRequest(PendingRequestManager.RequestType.LOGIN));
    }

    @Test
    void completeRequest_shouldCompleteFuture() throws ExecutionException, InterruptedException {
        // Arrange
        CompletableFuture<String> future = requestManager.registerRequest(PendingRequestManager.RequestType.LOGIN);

        // Act
        requestManager.completeRequest(PendingRequestManager.RequestType.LOGIN, "response");

        // Assert
        assertEquals("response", future.get());
        assertFalse(requestManager.hasPendingRequest(PendingRequestManager.RequestType.LOGIN));
    }

    @Test
    void failRequest_shouldCompleteFutureExceptionally() {
        // Arrange
        CompletableFuture<String> future = requestManager.registerRequest(PendingRequestManager.RequestType.LOGIN);

        // Act
        requestManager.failRequest(PendingRequestManager.RequestType.LOGIN, new RuntimeException("error"));

        // Assert
        assertThrows(ExecutionException.class, future::get);
        assertFalse(requestManager.hasPendingRequest(PendingRequestManager.RequestType.LOGIN));
    }

    @Test
    void clear_shouldFailAllPendingRequests() {
        // Arrange
        CompletableFuture<String> future1 = requestManager.registerRequest(PendingRequestManager.RequestType.LOGIN);
        CompletableFuture<String> future2 = requestManager.registerRequest(PendingRequestManager.RequestType.LOGOUT);

        // Act
        requestManager.clear();

        // Assert
        assertThrows(ExecutionException.class, future1::get);
        assertThrows(ExecutionException.class, future2::get);
        assertEquals(0, requestManager.getPendingCount());
    }
}