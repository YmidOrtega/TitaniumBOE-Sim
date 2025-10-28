package com.boe.simulator.server.session;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SessionStatisticsTest {

    private SessionStatistics sessionStatistics;

    @BeforeEach
    void setUp() {
        sessionStatistics = new SessionStatistics();
    }

    @Test
    void incrementTotalConnections_shouldIncrementCount() {
        // Arrange
        int initialCount = sessionStatistics.getTotalConnections();

        // Act
        sessionStatistics.incrementTotalConnections();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getTotalConnections());
    }

    @Test
    void incrementSuccessfulLogins_shouldIncrementCount() {
        // Arrange
        int initialCount = sessionStatistics.getSuccessfulLogins();

        // Act
        sessionStatistics.incrementSuccessfulLogins();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getSuccessfulLogins());
    }

    @Test
    void incrementFailedLogins_shouldIncrementCount() {
        // Arrange
        int initialCount = sessionStatistics.getFailedLogins();

        // Act
        sessionStatistics.incrementFailedLogins();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getFailedLogins());
    }

    @Test
    void incrementMessagesReceived_shouldIncrementCount() {
        // Arrange
        long initialCount = sessionStatistics.getTotalMessagesReceived();

        // Act
        sessionStatistics.incrementMessagesReceived();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getTotalMessagesReceived());
    }

    @Test
    void incrementMessagesSent_shouldIncrementCount() {
        // Arrange
        long initialCount = sessionStatistics.getTotalMessagesSent();

        // Act
        sessionStatistics.incrementMessagesSent();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getTotalMessagesSent());
    }

    @Test
    void incrementHeartbeatsReceived_shouldIncrementCount() {
        // Arrange
        long initialCount = sessionStatistics.getTotalHeartbeatsReceived();

        // Act
        sessionStatistics.incrementHeartbeatsReceived();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getTotalHeartbeatsReceived());
    }

    @Test
    void incrementHeartbeatsSent_shouldIncrementCount() {
        // Arrange
        long initialCount = sessionStatistics.getTotalHeartbeatsSent();

        // Act
        sessionStatistics.incrementHeartbeatsSent();

        // Assert
        assertEquals(initialCount + 1, sessionStatistics.getTotalHeartbeatsSent());
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        // Act
        String s = sessionStatistics.toString();

        // Assert
        assertTrue(s.contains("totalConnections=0"));
        assertTrue(s.contains("successfulLogins=0"));
        assertTrue(s.contains("failedLogins=0"));
        assertTrue(s.contains("messagesRx=0"));
        assertTrue(s.contains("messagesTx=0"));
        assertTrue(s.contains("heartbeatsRx=0"));
        assertTrue(s.contains("heartbeatsTx=0"));
    }
}