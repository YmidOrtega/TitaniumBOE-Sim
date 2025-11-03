package com.boe.simulator.server.persistence.service;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedMessage;
import com.boe.simulator.server.persistence.repository.MessageRepository;
import com.boe.simulator.server.persistence.util.SerializationUtil;

import java.time.Instant;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class MessageRepositoryService implements MessageRepository {
    private static final Logger LOGGER = Logger.getLogger(MessageRepositoryService.class.getName());
    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    public MessageRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
    }

    @Override
    public void save(PersistedMessage message) {
        try {
            byte[] value = serializer.serialize(message);

            // Save main message
            dbManager.put(RocksDBManager.CF_MESSAGES, message.getIndexKey().getBytes(), value);

            // Save indexes for efficient querying
            saveIndexes(message);

            LOGGER.fine("Saved message: " + message.messageId());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save message: " + message.messageId(), e);
            throw new RuntimeException("Failed to save message", e);
        }
    }

    private void saveIndexes(PersistedMessage message) throws Exception {
        byte[] messageIdBytes = message.messageId().getBytes();

        // User index
        if (message.getUserIndexKey() != null) {
            dbManager.put(RocksDBManager.CF_MESSAGES,
                    message.getUserIndexKey().getBytes(),
                    messageIdBytes);
        }

        // Type index
        dbManager.put(RocksDBManager.CF_MESSAGES,
                message.getTypeIndexKey().getBytes(),
                messageIdBytes);

        // Date index
        dbManager.put(RocksDBManager.CF_MESSAGES,
                message.getDateIndexKey().getBytes(),
                messageIdBytes);

        // Connection index
        dbManager.put(RocksDBManager.CF_MESSAGES,
                message.getConnectionIndexKey().getBytes(),
                messageIdBytes);
    }

    @Override
    public Optional<PersistedMessage> findById(String messageId) {
        try {
            String key = String.format("msg:%s", messageId);
            byte[] data = dbManager.get(RocksDBManager.CF_MESSAGES, key.getBytes());

            if (data == null) return Optional.empty();

            PersistedMessage message = serializer.deserialize(data, PersistedMessage.class);
            return Optional.of(message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find message: " + messageId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<PersistedMessage> findAll() {
        try {
            List<PersistedMessage> messages = new ArrayList<>();
            List<byte[]> keys = dbManager.getKeysWithPrefix(
                    RocksDBManager.CF_MESSAGES,
                    "msg:".getBytes());

            for (byte[] key : keys) {
                byte[] data = dbManager.get(RocksDBManager.CF_MESSAGES, key);
                if (data != null) {
                    PersistedMessage message = serializer.deserialize(data, PersistedMessage.class);
                    messages.add(message);
                }
            }

            return messages;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find all messages", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedMessage> findByUsername(String username) {
        try {
            String prefix = String.format("user:%s:", username);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find messages by username: " + username, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedMessage> findByUsername(String username, Instant start, Instant end) {
        return findByUsername(username).stream()
                .filter(m -> !m.timestamp().isBefore(start) && !m.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<PersistedMessage> findByMessageType(byte messageType) {
        try {
            String prefix = String.format("type:0x%02X:", messageType);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find messages by type: " + messageType, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedMessage> findByMessageType(byte messageType, Instant start, Instant end) {
        return findByMessageType(messageType).stream()
                .filter(m -> !m.timestamp().isBefore(start) && !m.timestamp().isAfter(end))
                .collect(Collectors.toList());
    }

    @Override
    public List<PersistedMessage> findByConnectionId(int connectionId) {
        try {
            String prefix = String.format("conn:%d:", connectionId);
            return findByIndexPrefix(prefix);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find messages by connection: " + connectionId, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedMessage> findByDirection(PersistedMessage.MessageDirection direction) {
        return findAll().stream()
                .filter(m -> m.direction() == direction)
                .collect(Collectors.toList());
    }

    @Override
    public List<PersistedMessage> findByDateRange(Instant start, Instant end) {
        return findAll().stream()
                .filter(m -> !m.timestamp().isBefore(start) && !m.timestamp().isAfter(end))
                .sorted(Comparator.comparing(PersistedMessage::timestamp))
                .collect(Collectors.toList());
    }

    @Override
    public List<PersistedMessage> findBySession(String username, String sessionSubID) {
        return findByUsername(username).stream()
                .filter(m -> sessionSubID.equals(m.sessionSubID()))
                .collect(Collectors.toList());
    }

    @Override
    public void delete(String messageId) {
        try {
            String key = String.format("msg:%s", messageId);
            dbManager.delete(RocksDBManager.CF_MESSAGES, key.getBytes());
            LOGGER.fine("Deleted message: " + messageId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to delete message: " + messageId, e);
            throw new RuntimeException("Failed to delete message", e);
        }
    }

    @Override
    public int deleteOlderThan(Instant cutoffDate) {
        List<PersistedMessage> oldMessages = findAll().stream()
                .filter(m -> m.timestamp().isBefore(cutoffDate))
                .toList();

        int deleted = 0;
        for (PersistedMessage message : oldMessages) {
            try {
                delete(message.messageId());
                deleted++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to delete old message: " + message.messageId(), e);
            }
        }

        LOGGER.info("Deleted " + deleted + " messages older than " + cutoffDate);
        return deleted;
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public long countByUsername(String username) {
        return findByUsername(username).size();
    }

    @Override
    public long countByMessageType(byte messageType) {
        return findByMessageType(messageType).size();
    }

    @Override
    public List<PersistedMessage> findLatest(int limit) {
        return findAll().stream()
                .sorted(Comparator.comparing(PersistedMessage::timestamp).reversed())
                .limit(limit)
                .collect(Collectors.toList());
    }

    @Override
    public List<PersistedMessage> search(MessageSearchCriteria criteria) {
        List<PersistedMessage> results = findAll();

        // Apply filters
        if (criteria.username() != null) {
            results = results.stream()
                    .filter(m -> criteria.username().equals(m.username()))
                    .collect(Collectors.toList());
        }

        if (criteria.connectionId() != null) {
            results = results.stream()
                    .filter(m -> m.connectionId() == criteria.connectionId())
                    .collect(Collectors.toList());
        }

        if (criteria.messageType() != null) {
            results = results.stream()
                    .filter(m -> m.messageType() == criteria.messageType())
                    .collect(Collectors.toList());
        }

        if (criteria.direction() != null) {
            results = results.stream()
                    .filter(m -> m.direction() == criteria.direction())
                    .collect(Collectors.toList());
        }

        if (criteria.startDate() != null) {
            results = results.stream()
                    .filter(m -> !m.timestamp().isBefore(criteria.startDate()))
                    .collect(Collectors.toList());
        }

        if (criteria.endDate() != null) {
            results = results.stream()
                    .filter(m -> !m.timestamp().isAfter(criteria.endDate()))
                    .collect(Collectors.toList());
        }

        // Sort by timestamp descending
        results = results.stream()
                .sorted(Comparator.comparing(PersistedMessage::timestamp).reversed())
                .collect(Collectors.toList());

        // Apply limit
        if (criteria.limit() != null && criteria.limit() > 0) {
            results = results.stream()
                    .limit(criteria.limit())
                    .collect(Collectors.toList());
        }

        return results;
    }

    private List<PersistedMessage> findByIndexPrefix(String prefix) throws Exception {
        List<PersistedMessage> messages = new ArrayList<>();
        List<byte[]> indexKeys = dbManager.getKeysWithPrefix(
                RocksDBManager.CF_MESSAGES,
                prefix.getBytes());

        for (byte[] indexKey : indexKeys) {
            byte[] messageIdBytes = dbManager.get(RocksDBManager.CF_MESSAGES, indexKey);
            if (messageIdBytes != null) {
                String messageId = new String(messageIdBytes);
                Optional<PersistedMessage> messageOpt = findById(messageId);
                messageOpt.ifPresent(messages::add);
            }
        }

        return messages.stream()
                .sorted(Comparator.comparing(PersistedMessage::timestamp).reversed())
                .collect(Collectors.toList());
    }
}