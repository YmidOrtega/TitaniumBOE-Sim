package com.boe.simulator.server.persistence.repository;

import com.boe.simulator.server.persistence.model.AuditEvent;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AuditRepository {

    void save(AuditEvent event);

    Optional<AuditEvent> findById(String eventId);

    List<AuditEvent> findAll();

    List<AuditEvent> findByType(AuditEvent.EventType eventType);

    List<AuditEvent> findByType(AuditEvent.EventType eventType, Instant start, Instant end);

    List<AuditEvent> findByUsername(String username);

    List<AuditEvent> findByUsername(String username, Instant start, Instant end);

    List<AuditEvent> findBySeverity(AuditEvent.EventSeverity severity);

    List<AuditEvent> findBySeverity(AuditEvent.EventSeverity severity, Instant start, Instant end);

    List<AuditEvent> findByDateRange(Instant start, Instant end);

    List<AuditEvent> findByConnectionId(int connectionId);

    void delete(String eventId);

    int deleteOlderThan(Instant cutoffDate);

    long count();

    long countByType(AuditEvent.EventType eventType);

    long countBySeverity(AuditEvent.EventSeverity severity);

    List<AuditEvent> findLatest(int limit);

    List<AuditEvent> search(AuditSearchCriteria criteria);

    record AuditSearchCriteria(
            AuditEvent.EventType eventType,
            AuditEvent.EventSeverity severity,
            String username,
            Integer connectionId,
            Instant startDate,
            Instant endDate,
            Integer limit
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private AuditEvent.EventType eventType;
            private AuditEvent.EventSeverity severity;
            private String username;
            private Integer connectionId;
            private Instant startDate;
            private Instant endDate;
            private Integer limit;

            public Builder eventType(AuditEvent.EventType eventType) {
                this.eventType = eventType;
                return this;
            }

            public Builder severity(AuditEvent.EventSeverity severity) {
                this.severity = severity;
                return this;
            }

            public Builder username(String username) {
                this.username = username;
                return this;
            }

            public Builder connectionId(int connectionId) {
                this.connectionId = connectionId;
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

            public AuditSearchCriteria build() {
                return new AuditSearchCriteria(
                        eventType,
                        severity,
                        username,
                        connectionId,
                        startDate,
                        endDate,
                        limit
                );
            }
        }
    }
}