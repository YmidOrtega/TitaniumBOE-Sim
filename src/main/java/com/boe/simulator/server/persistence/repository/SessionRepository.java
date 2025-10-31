package com.boe.simulator.server.persistence.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.boe.simulator.server.persistence.model.PersistedSession;

public interface SessionRepository {

    void save(PersistedSession session);

    Optional<PersistedSession> findById(String sessionId);

    List<PersistedSession> findByUsername(String username);

    List<PersistedSession> findByDateRange(Instant startDate, Instant endDate);

    List<PersistedSession> findByUsernameAndDateRange(
            String username,
            Instant startDate,
            Instant endDate
    );

    List<PersistedSession> findActiveSessions();

    List<PersistedSession> findSuccessfulLogins();

    List<PersistedSession> findFailedLogins();

    List<PersistedSession> findByDate(LocalDate date);

    long count();

    long countSuccessfulLogins();

    long countFailedLogins();

    long countByUsername(String username);

    int deleteSessionsOlderThan(Instant cutoffDate);
    
    void deleteAll();
}