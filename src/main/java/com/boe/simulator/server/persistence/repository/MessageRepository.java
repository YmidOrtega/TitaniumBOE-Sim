package com.boe.simulator.server.persistence.repository;

import com.boe.simulator.server.persistence.model.PersistedMessage;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface MessageRepository {

    void save(PersistedMessage message);

    Optional<PersistedMessage> findById(String messageId);

    List<PersistedMessage> findAll();

    List<PersistedMessage> findByUsername(String username);

    List<PersistedMessage> findByUsername(String username, Instant start, Instant end);

    List<PersistedMessage> findByMessageType(byte messageType);

    List<PersistedMessage> findByMessageType(byte messageType, Instant start, Instant end);

    List<PersistedMessage> findByConnectionId(int connectionId);

    List<PersistedMessage> findByDirection(PersistedMessage.MessageDirection direction);

    List<PersistedMessage> findByDateRange(Instant start, Instant end);

    List<PersistedMessage> findBySession(String username, String sessionSubID);

    void delete(String messageId);

    int deleteOlderThan(Instant cutoffDate);

    long count();

    long countByUsername(String username);

    long countByMessageType(byte messageType);

    List<PersistedMessage> findLatest(int limit);

    List<PersistedMessage> search(MessageSearchCriteria criteria);

    record MessageSearchCriteria(
            String username,
            Integer connectionId,
            Byte messageType,
            PersistedMessage.MessageDirection direction,
            Instant startDate,
            Instant endDate,
            Integer limit
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String username;
            private Integer connectionId;
            private Byte messageType;
            private PersistedMessage.MessageDirection direction;
            private Instant startDate;
            private Instant endDate;
            private Integer limit;

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder connectionId(int connectionId) {
                this.connectionId = connectionId;
                return this;
            }

            public Builder messageType(byte messageType) {
                this.messageType = messageType;
                return this;
            }

            public Builder direction(PersistedMessage.MessageDirection direction) {
                this.direction = direction;
                return this;
            }

            public Builder startDate(Instant startDate) {
                this.startDate = startDate;
                return this;
            }

            public Builder endDate(Instant endDate) {
                this.endDate = endDate;
                return this;
            }

            public Builder limit(int limit) {
                this.limit = limit;
                return this;
            }

            public MessageSearchCriteria build() {
                return new MessageSearchCriteria(
                        username,
                        connectionId,
                        messageType,
                        direction,
                        startDate,
                        endDate,
                        limit
                );
            }
        }
    }
}