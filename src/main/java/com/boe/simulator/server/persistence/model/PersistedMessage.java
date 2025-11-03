package com.boe.simulator.server.persistence.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public record PersistedMessage(
        @JsonProperty("messageId") String messageId,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("direction") MessageDirection direction,
        @JsonProperty("messageType") byte messageType,
        @JsonProperty("messageTypeName") String messageTypeName,
        @JsonProperty("connectionId") int connectionId,
        @JsonProperty("username") String username,
        @JsonProperty("sessionSubID") String sessionSubID,
        @JsonProperty("sequenceNumber") int sequenceNumber,
        @JsonProperty("rawData") byte[] rawData,
        @JsonProperty("length") int length,
        @JsonProperty("metadata") Map<String, String> metadata
) {

    @JsonCreator
    public PersistedMessage {
        if (messageId == null || messageId.isBlank()) throw new IllegalArgumentException("Message ID cannot be null or blank");

        if (timestamp == null) throw new IllegalArgumentException("Timestamp cannot be null");

        if (direction == null) throw new IllegalArgumentException("Direction cannot be null");

        if (messageTypeName == null) throw new IllegalArgumentException("Message type name cannot be null");

        if (rawData == null) throw new IllegalArgumentException("Raw data cannot be null");

        if (metadata == null) metadata = new HashMap<>();
    }

    public static PersistedMessage create(
            MessageDirection direction,
            byte messageType,
            String messageTypeName,
            int connectionId,
            String username,
            String sessionSubID,
            int sequenceNumber,
            byte[] rawData
    ) {
        String messageId = generateMessageId(direction, connectionId, sequenceNumber);

        return new PersistedMessage(
                messageId,
                Instant.now(),
                direction,
                messageType,
                messageTypeName,
                connectionId,
                username,
                sessionSubID,
                sequenceNumber,
                rawData.clone(),
                rawData.length,
                new HashMap<>()
        );
    }

    public PersistedMessage withMetadata(String key, String value) {
        Map<String, String> newMetadata = new HashMap<>(metadata);
        newMetadata.put(key, value);

        return new PersistedMessage(
                messageId,
                timestamp,
                direction,
                messageType,
                messageTypeName,
                connectionId,
                username,
                sessionSubID,
                sequenceNumber,
                rawData,
                length,
                newMetadata
        );
    }

    private static String generateMessageId(MessageDirection direction, int connectionId, int sequenceNumber) {
        long timestampMillis = System.currentTimeMillis();
        return String.format("%s-%d-%d-%d",
                direction.name(),
                connectionId,
                sequenceNumber,
                timestampMillis);
    }

    public String getIndexKey() {
        return String.format("msg:%s", messageId);
    }

    public String getUserIndexKey() {
        if (username == null || username.isBlank()) {
            return null;
        }
        return String.format("user:%s:%d", username, timestamp.toEpochMilli());
    }

    public String getTypeIndexKey() {
        return String.format("type:0x%02X:%d", messageType, timestamp.toEpochMilli());
    }

    public String getDateIndexKey() {
        String date = timestamp.toString().substring(0, 10); // YYYY-MM-DD
        return String.format("date:%s:%d", date, timestamp.toEpochMilli());
    }

    public String getConnectionIndexKey() {
        return String.format("conn:%d:%d", connectionId, timestamp.toEpochMilli());
    }

    public enum MessageDirection {
        INBOUND,   // Client -> Server
        OUTBOUND   // Server -> Client
    }

    public boolean isRequest() {
        return direction == MessageDirection.INBOUND;
    }

    public boolean isResponse() {
        return direction == MessageDirection.OUTBOUND;
    }

    @Override
    public String toString() {
        return "PersistedMessage{" +
                "id='" + messageId + '\'' +
                ", timestamp=" + timestamp +
                ", direction=" + direction +
                ", type=" + messageTypeName +
                ", user='" + username + '\'' +
                ", seq=" + sequenceNumber +
                ", length=" + length +
                '}';
    }
}