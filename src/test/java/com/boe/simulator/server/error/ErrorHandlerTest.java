package com.boe.simulator.server.error;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ErrorHandlerTest {

    private ErrorHandler errorHandler;

    @BeforeEach
    void setUp() {
        errorHandler = new ErrorHandler();
    }

    @Test
    void handleError_shouldIncrementCriticalErrorCount_forCriticalError() {
        // Arrange
        int initialErrors = errorHandler.getTotalErrors();

        // Act
        errorHandler.handleError(1, "test", new Exception());

        // Assert
        assertEquals(initialErrors + 1, errorHandler.getTotalErrors());
    }

    @Test
    void handleError_shouldIncrementWarningAndRecoveryCount_forRecoverableError() {
        // Arrange
        int initialWarnings = errorHandler.getTotalWarnings();
        int initialRecoveries = errorHandler.getTotalRecoveries();

        // Act
        errorHandler.handleError(1, "test", new java.io.IOException());

        // Assert
        assertEquals(initialWarnings + 1, errorHandler.getTotalWarnings());
        assertEquals(initialRecoveries + 1, errorHandler.getTotalRecoveries());
    }

    @Test
    void handleError_shouldNotIncrementCounts_forInformationalError() {
        // Arrange
        int initialErrors = errorHandler.getTotalErrors();
        int initialWarnings = errorHandler.getTotalWarnings();

        // Act
        errorHandler.handleError(1, "test", new java.net.SocketTimeoutException());

        // Assert
        assertEquals(initialErrors, errorHandler.getTotalErrors());
        assertEquals(initialWarnings, errorHandler.getTotalWarnings());
    }

    @Test
    void shouldTerminateConnection_shouldReturnTrue_whenErrorCountExceedsThreshold() {
        // Arrange
        for (int i = 0; i < 11; i++) {
            errorHandler.handleError(1, "test", new Exception());
        }

        // Act & Assert
        assertTrue(errorHandler.shouldTerminateConnection(1));
    }

    @Test
    void shouldTerminateConnection_shouldReturnFalse_whenErrorCountIsBelowThreshold() {
        // Arrange
        for (int i = 0; i < 10; i++) {
            errorHandler.handleError(1, "test", new Exception());
        }

        // Act & Assert
        assertFalse(errorHandler.shouldTerminateConnection(1));
    }

    @Test
    void clearConnectionStats_shouldResetErrorCountForConnection() {
        // Arrange
        for (int i = 0; i < 11; i++) {
            errorHandler.handleError(1, "test", new Exception());
        }
        assertTrue(errorHandler.shouldTerminateConnection(1));

        // Act
        errorHandler.clearConnectionStats(1);

        // Assert
        assertFalse(errorHandler.shouldTerminateConnection(1));
    }
}