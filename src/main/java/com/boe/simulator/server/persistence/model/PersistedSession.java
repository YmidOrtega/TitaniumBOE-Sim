package com.boe.simulator.server.persistence.model;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PersistedSession(
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("username") String username,
        @JsonProperty("sessionSubID") String sessionSubID,
        @JsonProperty("remoteAddress") String remoteAddress,
        @JsonProperty("startTime") Instant startTime,
        @JsonProperty("endTime") Instant endTime,
        @JsonProperty("durationSeconds") long durationSeconds,
        @JsonProperty("loginSuccessful") boolean loginSuccessful,
        @JsonProperty("loginFailureReason") String loginFailureReason,
        @JsonProperty("messagesReceived") int messagesReceived,
        @JsonProperty("messagesSent") int messagesSent,
        @JsonProperty("heartbeatsReceived") int heartbeatsReceived,
        @JsonProperty("heartbeatsSent") int heartbeatsSent,
        @JsonProperty("disconnectReason") String disconnectReason,
        @JsonProperty("metadata") Map<String, String> metadata
) {

    @JsonCreator
    public PersistedSession {
        if (sessionId == null || sessionId.isBlank()) throw new IllegalArgumentException("Session ID cannot be null or blank");
        if (username == null || username.isBlank()) throw new IllegalArgumentException("Username cannot be null or blank");
        if (startTime == null) throw new IllegalArgumentException("Start time cannot be null");
        if (metadata == null) metadata = new HashMap<>();
        
    }

    public static PersistedSession createOnLogin(
            int connectionId,
            String username,
            String sessionSubID,
            String remoteAddress,
            Instant startTime,
            boolean loginSuccessful,
            String loginFailureReason
    ) {
        String sessionId = generateSessionId(connectionId, startTime);
        
        return new PersistedSession(
                sessionId,
                username,
                sessionSubID,
                remoteAddress,
                startTime,
                null,
                0,
                loginSuccessful,
                loginFailureReason,
                0, 0, 0, 0,
                null,
                new HashMap<>()
        );
    }

    public PersistedSession closeSession(
            Instant endTime,
            int messagesReceived,
            int messagesSent,
            int heartbeatsReceived,
            int heartbeatsSent,
            String disconnectReason
    ) {
        long duration = Duration.between(startTime, endTime).getSeconds();
        
        return new PersistedSession(
                sessionId,
                username,
                sessionSubID,
                remoteAddress,
                startTime,
                endTime,
                duration,
                loginSuccessful,
                loginFailureReason,
                messagesReceived,
                messagesSent,
                heartbeatsReceived,
                heartbeatsSent,
                disconnectReason,
                metadata
        );
    }

    public PersistedSession withMetadata(String key, String value) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);
        
        return new PersistedSession(
                sessionId,
                username,
                sessionSubID,
                remoteAddress,
                startTime,
                endTime,
                durationSeconds,
                loginSuccessful,
                loginFailureReason,
                messagesReceived,
                messagesSent,
                heartbeatsReceived,
                heartbeatsSent,
                disconnectReason,
                newMetadata
        );
    }

    public boolean isActive() {
        return endTime == null;
    }

    public long getCalculatedDurationSeconds() {
        if (endTime == null) return Duration.between(startTime, Instant.now()).getSeconds();
        
        return durationSeconds;
    }

    private static String generateSessionId(int connectionId, Instant startTime) {
        return String.format("session_%d_%d", connectionId, startTime.toEpochMilli());
    }

    public String getStorageKey() {
        String date = startTime.toString().substring(0, 10);
        return String.format("session:%s:%s:%s", date, username, sessionId);
    }

    @Override
    public String toString() {
        return "PersistedSession{" +
                "id='" + sessionId + '\'' +
                ", user='" + username + '\'' +
                ", sessionSubID='" + sessionSubID + '\'' +
                ", start=" + startTime +
                ", duration=" + durationSeconds + "s" +
                ", loginOK=" + loginSuccessful +
                ", msgRx=" + messagesReceived +
                ", msgTx=" + messagesSent +
                ", active=" + isActive() +
                '}';
    }
}