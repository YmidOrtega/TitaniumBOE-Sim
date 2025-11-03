package com.boe.simulator.client.persistence.config;

import com.boe.simulator.server.persistence.config.ConfigVersion;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public record PersistedClientConfig(
        @JsonProperty("version") ConfigVersion version,
        @JsonProperty("host") String host,
        @JsonProperty("port") int port,
        @JsonProperty("username") String username,
        @JsonProperty("passwordHint") String passwordHint,
        @JsonProperty("sessionSubID") String sessionSubID,
        @JsonProperty("connectionTimeout") int connectionTimeout,
        @JsonProperty("readTimeout") int readTimeout,
        @JsonProperty("heartbeatIntervalSeconds") long heartbeatIntervalSeconds,
        @JsonProperty("autoHeartbeat") boolean autoHeartbeat,
        @JsonProperty("autoReconnect") boolean autoReconnect,
        @JsonProperty("maxReconnectAttempts") int maxReconnectAttempts,
        @JsonProperty("reconnectDelaySeconds") long reconnectDelaySeconds,
        @JsonProperty("logLevel") String logLevel,
        @JsonProperty("enableLogging") boolean enableLogging,
        @JsonProperty("metadata") Map<String, String> metadata
) {

    @JsonCreator
    public PersistedClientConfig {
        if (version == null) version = ConfigVersion.initial();

        if (host == null || host.isBlank()) throw new IllegalArgumentException("Host cannot be null or blank");

        if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");

        if (metadata == null) metadata = new HashMap<>();

    }

    public static PersistedClientConfig createDefault() {
        return new PersistedClientConfig(
                ConfigVersion.initial(),
                "localhost",
                8080,
                "",
                "",
                "",
                30000,
                30000,
                10,
                true,
                false,
                3,
                5,
                Level.INFO.getName(),
                true,
                new HashMap<>()
        );
    }

    public static PersistedClientConfig fromProfile(String profileName, String host, int port, String username) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("profile", profileName);

        return new PersistedClientConfig(
                ConfigVersion.initial(),
                host,
                port,
                username,
                "",
                "",
                30000,
                30000,
                10,
                true,
                false,
                3,
                5,
                Level.INFO.getName(),
                true,
                metadata
        );
    }

    public PersistedClientConfig withVersion(String modifiedBy, String description) {
        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, description),
                host,
                port,
                username,
                passwordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                autoHeartbeat,
                autoReconnect,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                metadata
        );
    }

    public PersistedClientConfig withConnection(String newHost, int newPort, String modifiedBy) {
        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, "Changed connection to " + newHost + ":" + newPort),
                newHost,
                newPort,
                username,
                passwordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                autoHeartbeat,
                autoReconnect,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                metadata
        );
    }

    public PersistedClientConfig withCredentials(String newUsername, String newPasswordHint, String modifiedBy) {
        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, "Changed credentials for " + newUsername),
                host,
                port,
                newUsername,
                newPasswordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                autoHeartbeat,
                autoReconnect,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                metadata
        );
    }

    public PersistedClientConfig withAutoHeartbeat(boolean enabled, String modifiedBy) {
        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, "Changed auto-heartbeat to " + enabled),
                host,
                port,
                username,
                passwordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                enabled,
                autoReconnect,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                metadata
        );
    }

    public PersistedClientConfig withAutoReconnect(boolean enabled, String modifiedBy) {
        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, "Changed auto-reconnect to " + enabled),
                host,
                port,
                username,
                passwordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                autoHeartbeat,
                enabled,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                metadata
        );
    }

    public PersistedClientConfig withMetadata(String key, String value, String modifiedBy) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);

        return new PersistedClientConfig(
                ConfigVersion.next(version, modifiedBy, "Added metadata: " + key),
                host,
                port,
                username,
                passwordHint,
                sessionSubID,
                connectionTimeout,
                readTimeout,
                heartbeatIntervalSeconds,
                autoHeartbeat,
                autoReconnect,
                maxReconnectAttempts,
                reconnectDelaySeconds,
                logLevel,
                enableLogging,
                newMetadata
        );
    }

    @JsonIgnore
    public boolean isValid() {
        try {
            if (port < 1 || port > 65535) return false;

            if (connectionTimeout < 1000) return false;

            if (readTimeout < 1000) return false;

            if (heartbeatIntervalSeconds < 1) return false;

            if (maxReconnectAttempts < 0) return false;

            if (reconnectDelaySeconds < 0) return false;

            Level.parse(logLevel);

            return true;
        } catch (Exception e) {
            return false;
        }
    }
}