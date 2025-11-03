package com.boe.simulator.server.persistence.config;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PersistedServerConfig(
        @JsonProperty("version") ConfigVersion version,
        @JsonProperty("host") String host,
        @JsonProperty("port") int port,
        @JsonProperty("maxConnections") int maxConnections,
        @JsonProperty("connectionTimeout") int connectionTimeout,
        @JsonProperty("heartbeatIntervalSeconds") long heartbeatIntervalSeconds,
        @JsonProperty("heartbeatTimeoutSeconds") long heartbeatTimeoutSeconds,
        @JsonProperty("logLevel") String logLevel,
        @JsonProperty("databasePath") String databasePath,
        @JsonProperty("enablePersistence") boolean enablePersistence,
        @JsonProperty("enableMetrics") boolean enableMetrics,
        @JsonProperty("metricsPort") int metricsPort,
        @JsonProperty("metadata") Map<String, String> metadata
) {

    @JsonCreator
    public PersistedServerConfig {
        if (version == null) version = ConfigVersion.initial();

        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host cannot be null or blank");

        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");

        if (maxConnections < 1) throw new IllegalArgumentException("Max connections must be at least 1");

        if (metadata == null) metadata = new HashMap<>();
    }

    public static PersistedServerConfig createDefault() {
        return new PersistedServerConfig(
                ConfigVersion.initial(),
                "0.0.0.0",
                8080,
                100,
                30000,
                10,
                30,
                Level.INFO.getName(),
                "./data/cboe_server",
                true,
                false,
                9090,
                new HashMap<>()
        );
    }

    public PersistedServerConfig withVersion(String modifiedBy, String description) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, description),
                host,
                port,
                maxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withHost(String newHost, String modifiedBy) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Changed host to " + newHost),
                newHost,
                port,
                maxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withPort(int newPort, String modifiedBy) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Changed port to " + newPort),
                host,
                newPort,
                maxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withMaxConnections(int newMaxConnections, String modifiedBy) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Changed max connections to " + newMaxConnections),
                host,
                port,
                newMaxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withHeartbeatInterval(long newInterval, String modifiedBy) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Changed heartbeat interval to " + newInterval + "s"),
                host,
                port,
                maxConnections,
                connectionTimeout,
                newInterval,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withLogLevel(String newLogLevel, String modifiedBy) {
        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Changed log level to " + newLogLevel),
                host,
                port,
                maxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                newLogLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                metadata
        );
    }

    public PersistedServerConfig withMetadata(String key, String value, String modifiedBy) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);

        return new PersistedServerConfig(
                ConfigVersion.next(version, modifiedBy, "Added metadata: " + key),
                host,
                port,
                maxConnections,
                connectionTimeout,
                heartbeatIntervalSeconds,
                heartbeatTimeoutSeconds,
                logLevel,
                databasePath,
                enablePersistence,
                enableMetrics,
                metricsPort,
                newMetadata
        );
    }

    @JsonIgnore
    public boolean isValid() {
        try {
            // Validar puerto
            if (port < 1 || port > 65535) return false;

            // Validar max connections
            if (maxConnections < 1) return false;

            // Validar timeouts
            if (connectionTimeout < 1000) return false;

            // Validar heartbeat
            if (heartbeatIntervalSeconds < 1) return false;
            if (heartbeatTimeoutSeconds < 5) return false;

            // Validar log level
            Level.parse(logLevel);

            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}