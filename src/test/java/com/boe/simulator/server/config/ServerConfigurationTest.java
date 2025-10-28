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
        assertEquals(10, config.getHeartbeatIntervalSeconds());
        assertEquals(30, config.getHeartbeatTimeoutSeconds());
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
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().port(0));
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().port(65536));
    }

    @Test
    void builder_shouldThrowException_forInvalidMaxConnections() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().maxConnections(0));
    }

    @Test
    void builder_shouldThrowException_forInvalidConnectionTimeout() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().connectionTimeout(999));
    }

    @Test
    void builder_shouldThrowException_forInvalidHeartbeatInterval() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().heartbeatIntervalSeconds(0));
    }

    @Test
    void builder_shouldThrowException_forInvalidHeartbeatTimeout() {
        // Assert
        assertThrows(IllegalArgumentException.class, () -> ServerConfiguration.builder().heartbeatTimeoutSeconds(4));
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