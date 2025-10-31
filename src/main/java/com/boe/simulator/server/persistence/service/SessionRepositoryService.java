package com.boe.simulator.server.persistence.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.RocksDBException;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedSession;
import com.boe.simulator.server.persistence.repository.SessionRepository;
import com.boe.simulator.server.persistence.util.SerializationUtil;

public class SessionRepositoryService implements SessionRepository {
    private static final Logger LOGGER = Logger.getLogger(SessionRepositoryService.class.getName());
    
    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    public SessionRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
    }

    @Override
    public void save(PersistedSession session) {
        try {
            String key = session.getStorageKey();
            byte[] value = serializer.serialize(session);
            dbManager.put(RocksDBManager.CF_SESSIONS, key.getBytes(), value);
            
            LOGGER.log(Level.FINE, "Saved session: {0}", session.sessionId());
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to save session: " + session.sessionId(), e);
            throw new RuntimeException("Failed to save session", e);
        }
    }

    @Override
    public Optional<PersistedSession> findById(String sessionId) {
        try {
            List<PersistedSession> allSessions = findAll();
            return allSessions.stream()
                    .filter(s -> s.sessionId().equals(sessionId))
                    .findFirst();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find session: " + sessionId, e);
            return Optional.empty();
        }
    }

    @Override
    public List<PersistedSession> findByUsername(String username) {
        try {
            String prefix = "session:";
            List<byte[]> keys = dbManager.getKeysWithPrefix(RocksDBManager.CF_SESSIONS, prefix.getBytes());
            
            List<PersistedSession> sessions = new ArrayList<>();
            for (byte[] keyBytes : keys) {
                String key = new String(keyBytes);
                if (key.contains(":" + username + ":")) {
                    byte[] data = dbManager.get(RocksDBManager.CF_SESSIONS, keyBytes);
                    if (data != null) {
                        PersistedSession session = serializer.deserialize(data, PersistedSession.class);
                        sessions.add(session);
                    }
                }
            }
            
            LOGGER.log(Level.FINE, "Found {0} sessions for user: {1}", new Object[]{sessions.size(), username});
            return sessions;
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find sessions for user: " + username, e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findByDateRange(Instant startDate, Instant endDate) {
        try {
            List<PersistedSession> allSessions = findAll();
            return allSessions.stream()
                    .filter(s -> !s.startTime().isBefore(startDate) && !s.startTime().isAfter(endDate))
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find sessions by date range", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findByUsernameAndDateRange(
            String username,
            Instant startDate,
            Instant endDate
    ) {
        try {
            return findByUsername(username).stream()
                    .filter(s -> !s.startTime().isBefore(startDate) && !s.startTime().isAfter(endDate))
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find sessions by username and date range", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findActiveSessions() {
        try {
            List<PersistedSession> allSessions = findAll();
            return allSessions.stream()
                    .filter(PersistedSession::isActive)
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find active sessions", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findSuccessfulLogins() {
        try {
            List<PersistedSession> allSessions = findAll();
            return allSessions.stream()
                    .filter(PersistedSession::loginSuccessful)
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find successful logins", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findFailedLogins() {
        try {
            List<PersistedSession> allSessions = findAll();
            return allSessions.stream()
                    .filter(s -> !s.loginSuccessful())
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find failed logins", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedSession> findByDate(LocalDate date) {
        try {
            String datePrefix = "session:" + date.toString();
            List<byte[]> keys = dbManager.getKeysWithPrefix(
                    RocksDBManager.CF_SESSIONS,
                    datePrefix.getBytes()
            );
            
            List<PersistedSession> sessions = new ArrayList<>();
            for (byte[] keyBytes : keys) {
                byte[] data = dbManager.get(RocksDBManager.CF_SESSIONS, keyBytes);
                if (data != null) {
                    PersistedSession session = serializer.deserialize(data, PersistedSession.class);
                    sessions.add(session);
                }
            }
            
            return sessions;
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find sessions by date: " + date, e);
            return new ArrayList<>();
        }
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public long countSuccessfulLogins() {
        return findSuccessfulLogins().size();
    }

    @Override
    public long countFailedLogins() {
        return findFailedLogins().size();
    }

    @Override
    public long countByUsername(String username) {
        return findByUsername(username).size();
    }

    @Override
    public int deleteSessionsOlderThan(Instant cutoffDate) {
        try {
            List<PersistedSession> oldSessions = findAll().stream()
                    .filter(s -> s.startTime().isBefore(cutoffDate))
                    .toList();
            
            int deleted = 0;
            for (PersistedSession session : oldSessions) {
                String key = session.getStorageKey();
                dbManager.delete(RocksDBManager.CF_SESSIONS, key.getBytes());
                deleted++;
            }
            
            LOGGER.log(Level.INFO, "Deleted {0} sessions older than {1}", new Object[]{deleted, cutoffDate});
            return deleted;
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete old sessions", e);
            return 0;
        }
    }

    @Override
    public void deleteAll() {
        try {
            List<PersistedSession> allSessions = findAll();
            for (PersistedSession session : allSessions) {
                String key = session.getStorageKey();
                dbManager.delete(RocksDBManager.CF_SESSIONS, key.getBytes());
            }
            LOGGER.info("Deleted all sessions");
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete all sessions", e);
            throw new RuntimeException("Failed to delete all sessions", e);
        }
    }

    private List<PersistedSession> findAll() {
        try {
            List<PersistedSession> sessions = new ArrayList<>();
            Map<byte[], byte[]> allData = dbManager.getAll(RocksDBManager.CF_SESSIONS);
            
            for (byte[] data : allData.values()) {
                PersistedSession session = serializer.deserialize(data, PersistedSession.class);
                sessions.add(session);
            }
            
            return sessions;
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find all sessions", e);
            return new ArrayList<>();
        }
    }
}