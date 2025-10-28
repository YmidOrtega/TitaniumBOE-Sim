package com.boe.simulator.server.metrics;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HealthMetricsTest {

    private HealthMetrics healthMetrics;

    @BeforeEach
    void setUp() {
        healthMetrics = new HealthMetrics();
    }

    @Test
    void recordBytesReceived_shouldIncrementTotalBytesReceived() {
        // Arrange
        long initialBytes = healthMetrics.getTotalBytesReceived();

        // Act
        healthMetrics.recordBytesReceived(100);

        // Assert
        assertEquals(initialBytes + 100, healthMetrics.getTotalBytesReceived());
    }

    @Test
    void recordBytesSent_shouldIncrementTotalBytesSent() {
        // Arrange
        long initialBytes = healthMetrics.getTotalBytesSent();

        // Act
        healthMetrics.recordBytesSent(200);

        // Assert
        assertEquals(initialBytes + 200, healthMetrics.getTotalBytesSent());
    }

    @Test
    void updatePeakConnections_shouldUpdatePeak_whenCurrentIsHigher() {
        // Arrange
        healthMetrics.updatePeakConnections(5);

        // Act
        healthMetrics.updatePeakConnections(10);

        // Assert
        assertEquals(10, healthMetrics.getPeakActiveConnections());
    }

    @Test
    void updatePeakConnections_shouldNotUpdatePeak_whenCurrentIsLower() {
        // Arrange
        healthMetrics.updatePeakConnections(10);

        // Act
        healthMetrics.updatePeakConnections(5);

        // Assert
        assertEquals(10, healthMetrics.getPeakActiveConnections());
    }

    @Test
    void getHealthSummary_shouldReturnCorrectSummary() {
        // Arrange
        healthMetrics.recordBytesReceived(1024);
        healthMetrics.recordBytesSent(2048);
        healthMetrics.updatePeakConnections(15);

        // Act
        String summary = healthMetrics.getHealthSummary();

        // Assert
        assertTrue(summary.contains("BytesRx=1024"));
        assertTrue(summary.contains("BytesTx=2048"));
        assertTrue(summary.contains("PeakConnections=15"));
    }
}