package com.boe.simulator.server.persistence.service;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.AuditEvent;
import com.boe.simulator.server.persistence.repository.AuditRepository;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class AuditRepositoryService implements AuditRepository {
    private static final Logger LOGGER = Logger.getLogger(AuditRepositoryService.class.getName());
    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    public AuditRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
    }

    @Override
    public void save(AuditEvent event) {
        try {
            byte[] value = serializer.serialize(event);

            // Save main event
            dbManager.put(RocksDBManager.CF_AUDIT, event.getIndexKey().getBytes(), value);

            // Save indexes for efficient querying
            saveIndexes(event);

            LOGGER.fine("Saved audit event: " + event.eventId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save audit event: " + event.eventId(), e);
            throw new RuntimeException("Failed to save audit event", e);
        }
    }

    private void saveIndexes(AuditEvent event) throws Exception {
        byte[] eventIdBytes = event.eventId().getBytes();

        // Type index
        dbManager.put(RocksDBManager.CF_AUDIT,
                event.getTypeIndexKey().getBytes(),
                eventIdBytes);

        // User index
        if (event.getUserIndexKey() != null) {
            dbManager.put(RocksDBManager.CF_AUDIT,
                    event.getUserIndexKey().getBytes(),
                    eventIdBytes);
        }

        // Severity index
        dbManager.put(RocksDBManager.CF_AUDIT,
                event.getSeverityIndexKey().getBytes(),
                eventIdBytes);

        // Date index
        dbManager.put(RocksDBManager.CF_AUDIT,
                event.getDateIndexKey().getBytes(),
                eventIdBytes);
    }

    @Override
    public Optional<AuditEvent> findById(String eventId) {
        try {
            String key = String.format("audit:%s", eventId);
            byte[] data = dbManager.get(RocksDBManager.CF_AUDIT, key.getBytes());

            if (data == null) return Optional.empty();

            AuditEvent event = serializer.deserialize(data, AuditEvent.class);
            return Optional.of(event);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find audit event: " + eventId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<AuditEvent> findAll() {
        try {
            List<AuditEvent> events = new ArrayList<>();
            List<byte[]> keys = dbManager.getKeysWithPrefix(
                    RocksDBManager.CF_AUDIT,
                    "audit:".getBytes());

            for (byte[] key : keys) {
                String keyStr = new String(key);
                // Only get main audit records, not indexes
                if (keyStr.startsWith("audit:") && !keyStr.contains("-type:") &&
                        !keyStr.contains("-user:") && !keyStr.contains("-severity:") &&
                        !keyStr.contains("-date:")) {
                    byte[] data = dbManager.get(RocksDBManager.CF_AUDIT, key);
                    if (data != null) {
                        AuditEvent event = serializer.deserialize(data, AuditEvent.class);
                        events.add(event);
                    }
                }
            }

            return events;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find all audit events", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AuditEvent> findByType(AuditEvent.EventType eventType) {
        try {
            String prefix = String.format("audit-type:%s:", eventType.name());
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find events by type: " + eventType, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AuditEvent> findByType(AuditEvent.EventType eventType, Instant start, Instant end) {
        return findByType(eventType).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findByUsername(String username) {
        try {
            String prefix = String.format("audit-user:%s:", username);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find events by username: " + username, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AuditEvent> findByUsername(String username, Instant start, Instant end) {
        return findByUsername(username).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findBySeverity(AuditEvent.EventSeverity severity) {
        try {
            String prefix = String.format("audit-severity:%s:", severity.name());
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find events by severity: " + severity, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<AuditEvent> findBySeverity(AuditEvent.EventSeverity severity, Instant start, Instant end) {
        return findBySeverity(severity).stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findByDateRange(Instant start, Instant end) {
        return findAll().stream()
                .filter(e -> !e.timestamp().isBefore(start) && !e.timestamp().isAfter(end))
                .sorted(Comparator.comparing(AuditEvent::timestamp))
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> findByConnectionId(int connectionId) {
        return findAll().stream()
                .filter(e -> e.connectionId() != null && e.connectionId() == connectionId)
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String eventId) {
        try {
            String key = String.format("audit:%s", eventId);
            dbManager.delete(RocksDBManager.CF_AUDIT, key.getBytes());
            LOGGER.fine("Deleted audit event: " + eventId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete audit event: " + eventId, e);
            throw new RuntimeException("Failed to delete audit event", e);
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoffDate) {
        List<AuditEvent> oldEvents = findAll().stream()
                .filter(e -> e.timestamp().isBefore(cutoffDate))
                .toList();

        int deleted = 0;
        for (AuditEvent event : oldEvents) {
            try {
                delete(event.eventId());
                deleted++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete old event: " + event.eventId(), e);
            }
        }

        LOGGER.info("Deleted " + deleted + " audit events older than " + cutoffDate);
        return deleted;
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public long countByType(AuditEvent.EventType eventType) {
        return findByType(eventType).size();
    }

    @Override
    public long countBySeverity(AuditEvent.EventSeverity severity) {
        return findBySeverity(severity).size();
    }

    @Override
    public List<AuditEvent> findLatest(int limit) {
        return findAll().stream()
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<AuditEvent> search(AuditSearchCriteria criteria) {
        List<AuditEvent> results = findAll();

        // Apply filters
        if (criteria.eventType() != null) {
            results = results.stream()
                    .filter(e -> e.eventType() == criteria.eventType())
                    .collect(Collectors.toList());
        }

        if (criteria.severity() != null) {
            results = results.stream()
                    .filter(e -> e.severity() == criteria.severity())
                    .collect(Collectors.toList());
        }

        if (criteria.username() != null) {
            results = results.stream()
                    .filter(e -> criteria.username().equals(e.username()))
                    .collect(Collectors.toList());
        }

        if (criteria.connectionId() != null) {
            results = results.stream()
                    .filter(e -> e.connectionId() != null &&
                            e.connectionId().equals(criteria.connectionId()))
                    .collect(Collectors.toList());
        }

        if (criteria.startDate() != null) {
            results = results.stream()
                    .filter(e -> !e.timestamp().isBefore(criteria.startDate()))
                    .collect(Collectors.toList());
        }

        if (criteria.endDate() != null) {
            results = results.stream()
                    .filter(e -> !e.timestamp().isAfter(criteria.endDate()))
                    .collect(Collectors.toList());
        }

        // Sort by timestamp descending
        results = results.stream()
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .collect(Collectors.toList());

        // Apply limit
        if (criteria.limit() != null && criteria.limit() > 0) {
            results = results.stream()
                    .limit(criteria.limit())
                    .collect(Collectors.toList());
        }

        return results;
    }

    private List<AuditEvent> findByIndexPrefix(String prefix) throws Exception {
        List<AuditEvent> events = new ArrayList<>();
        List<byte[]> indexKeys = dbManager.getKeysWithPrefix(
                RocksDBManager.CF_AUDIT,
                prefix.getBytes());

        for (byte[] indexKey : indexKeys) {
            byte[] eventIdBytes = dbManager.get(RocksDBManager.CF_AUDIT, indexKey);
            if (eventIdBytes != null) {
                String eventId = new String(eventIdBytes);
                Optional<AuditEvent> eventOpt = findById(eventId);
                eventOpt.ifPresent(events::add);
            }
        }

        return events.stream()
                .sorted(Comparator.comparing(AuditEvent::timestamp).reversed())
                .collect(Collectors.toList());
    }
}