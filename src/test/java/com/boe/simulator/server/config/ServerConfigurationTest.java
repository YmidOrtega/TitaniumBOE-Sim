package com.boe.simulator.server.config;

import org.junit.jupiter.api.Test;

import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.*;

class ServerConfigurationTest {

    @Test
    void builder_shouldBuildWithDefaultValues() {
        // Act
        ServerConfiguration config = ServerConfiguration.builder().build();

        // Assert
        assertEquals("0.0.0.0", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals(100, config.getMaxConnections());
        assertEquals(30000, config.getConnectionTimeout());
        assertEquals(1, config.getHeartbeatIntervalSeconds());
        assertEquals(5, config.getHeartbeatTimeoutSeconds());
        assertEquals(Level.INFO, config.getLogLevel());
    }

    @Test
    void builder_shouldBuildWithCustomValues() {
        // Act
        ServerConfiguration config = ServerConfiguration.builder()
                .host("localhost")
                .port(9090)
                .maxConnections(50)
                .connectionTimeout(10000)
                .heartbeatIntervalSeconds(5)
                .heartbeatTimeoutSeconds(15)
                .logLevel(Level.WARNING)
                .build();

        // Assert
        assertEquals("localhost", config.getHost());
        assertEquals(9090, config.getPort());
        assertEquals(50, config.getMaxConnections());
        assertEquals(10000, config.getConnectionTimeout());
        assertEquals(5, config.getHeartbeatIntervalSeconds());
        assertEquals(15, config.getHeartbeatTimeoutSeconds());
        assertEquals(Level.WARNING, config.getLogLevel());
    }

    @Test
    void builder_shouldThrowException_forInvalidPort() {
        // Assert
        ServerConfiguration.Builder builder = ServerConfiguration.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.port(0));
        assertThrows(IllegalArgumentException.class, () -> builder.port(65536));
    }

    @Test
    void builder_shouldThrowException_forInvalidMaxConnections() {
        // Assert
        ServerConfiguration.Builder builder = ServerConfiguration.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.maxConnections(0));
    }

    @Test
    void builder_shouldThrowException_forInvalidConnectionTimeout() {
        // Assert
        ServerConfiguration.Builder builder = ServerConfiguration.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.connectionTimeout(999));
    }

    @Test
    void builder_shouldThrowException_forInvalidHeartbeatInterval() {
        // Assert
        ServerConfiguration.Builder builder = ServerConfiguration.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.heartbeatIntervalSeconds(0));
    }

    @Test
    void builder_shouldThrowException_forInvalidHeartbeatTimeout() {
        // Assert
        ServerConfiguration.Builder builder = ServerConfiguration.builder();
        assertThrows(IllegalArgumentException.class, () -> builder.heartbeatTimeoutSeconds(4));
    }

    @Test
    void getDefault_shouldReturnDefaultConfiguration() {
        // Act
        ServerConfiguration config = ServerConfiguration.getDefault();

        // Assert
        assertEquals("0.0.0.0", config.getHost());
        assertEquals(8080, config.getPort());
        assertEquals(100, config.getMaxConnections());
    }
}