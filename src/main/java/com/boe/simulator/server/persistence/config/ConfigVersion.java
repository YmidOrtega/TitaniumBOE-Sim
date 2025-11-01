package com.boe.simulator.server.persistence.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;


public record ConfigVersion(
        @JsonProperty("version") int version,
        @JsonProperty("createdAt") Instant createdAt,
        @JsonProperty("modifiedBy") String modifiedBy,
        @JsonProperty("description") String description
) {

    @JsonCreator
    public ConfigVersion {
        if (version < 0) throw new IllegalArgumentException("Version cannot be negative");
        if (createdAt == null) createdAt = Instant.now();
    }

    public static ConfigVersion initial() {
        return new ConfigVersion(1, Instant.now(), "system", "Initial configuration");
    }

    public static ConfigVersion next(ConfigVersion current, String modifiedBy, String description) {
        return new ConfigVersion(
                current.version + 1,
                Instant.now(),
                modifiedBy,
                description
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConfigVersion that = (ConfigVersion) o;
        return version == that.version;
    }

    @Override
    public int hashCode() {
        return Objects.hash(version);
    }
}
