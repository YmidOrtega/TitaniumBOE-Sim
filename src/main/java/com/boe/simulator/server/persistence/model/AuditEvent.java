package com.boe.simulator.server.persistence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record AuditEvent(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("eventType") EventType eventType,
        @JsonProperty("severity") EventSeverity severity,
        @JsonProperty("connectionId") Integer connectionId,
        @JsonProperty("username") String username,
        @JsonProperty("sessionSubID") String sessionSubID,
        @JsonProperty("description") String description,
        @JsonProperty("details") Map<String, String> details
) {

    @JsonCreator
    public AuditEvent {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("Event ID cannot be null or " + "blank");

        if (timestamp == null) throw new IllegalArgumentException("Timestamp cannot be null");

        if (eventType == null) throw new IllegalArgumentException("Event type cannot be null");

        if (severity == null) throw new IllegalArgumentException("Severity cannot be null");

        if (description == null) description = "";

        if (details == null) details = new HashMap<>();
    }

    public static AuditEvent create(
            EventType eventType,
            EventSeverity severity,
            String description
    ) {
        return create(eventType, severity, null, null, null, description, new HashMap<>());
    }

    public static AuditEvent create(
            EventType eventType,
            EventSeverity severity,
            Integer connectionId,
            String username,
            String sessionSubID,
            String description,
            Map<String, String> details
    ) {
        String eventId = generateEventId(eventType);

        return new AuditEvent(
                eventId,
                Instant.now(),
                eventType,
                severity,
                connectionId,
                username,
                sessionSubID,
                description,
                new HashMap<>(details)
        );
    }

    public AuditEvent withDetail(String key, String value) {
        Map<String, String> newDetails = new HashMap<>(details);
        newDetails.put(key, value);

        return new AuditEvent(
                eventId,
                timestamp,
                eventType,
                severity,
                connectionId,
                username,
                sessionSubID,
                description,
                newDetails
        );
    }

    private static String generateEventId(EventType eventType) {
        long timestampMillis = System.currentTimeMillis();
        return String.format("%s-%d", eventType.name(), timestampMillis);
    }

    public String getIndexKey() {
        return String.format("audit:%s", eventId);
    }

    public String getTypeIndexKey() {
        return String.format("audit-type:%s:%d", eventType.name(), timestamp.toEpochMilli());
    }

    public String getUserIndexKey() {
        if (username == null || username.isBlank()) {
            return null;
        }
        return String.format("audit-user:%s:%d", username, timestamp.toEpochMilli());
    }

    public String getSeverityIndexKey() {
        return String.format("audit-severity:%s:%d", severity.name(), timestamp.toEpochMilli());
    }

    public String getDateIndexKey() {
        String date = timestamp.toString().substring(0, 10); // YYYY-MM-DD
        return String.format("audit-date:%s:%d", date, timestamp.toEpochMilli());
    }

    public enum EventType {
        // Connection events
        CONNECTION_ACCEPTED,
        CONNECTION_REJECTED,
        CONNECTION_CLOSED,

        // Authentication events
        LOGIN_SUCCESS,
        LOGIN_FAILURE,
        LOGOUT,
        SESSION_TIMEOUT,
        DUPLICATE_SESSION,

        // Message events
        MESSAGE_RECEIVED,
        MESSAGE_SENT,
        MESSAGE_INVALID,
        MESSAGE_REJECTED,

        // Heartbeat events
        HEARTBEAT_TIMEOUT,
        HEARTBEAT_RESTORED,

        // Security events
        RATE_LIMIT_EXCEEDED,
        AUTHENTICATION_ERROR,
        AUTHORIZATION_ERROR,

        // System events
        SERVER_STARTED,
        SERVER_STOPPED,
        DATABASE_ERROR,
        CONFIGURATION_CHANGED,

        // Error events
        ERROR_OCCURRED,
        ERROR_RECOVERED
    }

    public enum EventSeverity {
        DEBUG,
        INFO,
        WARNING,
        ERROR,
        CRITICAL
    }

    @Override
    public String toString() {
        return "AuditEvent{" +
                "id='" + eventId + '\'' +
                ", timestamp=" + timestamp +
                ", type=" + eventType +
                ", severity=" + severity +
                ", user='" + username + '\'' +
                ", description='" + description + '\'' +
                '}';
    }
}