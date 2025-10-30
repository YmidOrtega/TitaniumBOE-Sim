package com.boe.simulator.server.persistence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record PersistedUser(
        @JsonProperty("username") String username,
        @JsonProperty("passwordHash") String passwordHash,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("lastLogin") Instant lastLogin,
        @JsonProperty("loginCount") int loginCount,
        @JsonProperty("active") boolean active,
        @JsonProperty("metadata") Map<String, String> metadata
) {

    @JsonCreator
    public PersistedUser {
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username cannot be null or blank");
        if (passwordHash == null || passwordHash.isBlank()) throw new IllegalArgumentException("Password hash cannot be null or blank");
        if (metadata == null) metadata = new HashMap<>();
    }

    public static PersistedUser create(String username, String passwordHash) {
        return new PersistedUser(
                username,
                passwordHash,
                Instant.now(),
                null,
                0,
                true,
                new HashMap<>()
        );
    }

    public PersistedUser withLogin() {
        return new PersistedUser(
                username,
                passwordHash,
                createdAt,
                Instant.now(),
                loginCount + 1,
                active,
                metadata
        );
    }

    public PersistedUser withPasswordHash(String newPasswordHash) {
        return new PersistedUser(
                username,
                newPasswordHash,
                createdAt,
                lastLogin,
                loginCount,
                active,
                metadata
        );
    }

    public PersistedUser deactivate() {
        return new PersistedUser(
                username,
                passwordHash,
                createdAt,
                lastLogin,
                loginCount,
                false,
                metadata
        );
    }

    public PersistedUser withMetadata(String key, String value) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        return new PersistedUser(
                username,
                passwordHash,
                createdAt,
                lastLogin,
                loginCount,
                active,
                newMetadata
        );
    }
}

