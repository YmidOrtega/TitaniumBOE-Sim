package com.boe.simulator.server.persistence.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.boe.simulator.server.persistence.model.PersistedStatistics;

public interface StatisticsRepository {

    void save(PersistedStatistics statistics);

    Optional<PersistedStatistics> findByDate(LocalDate date);

    List<PersistedStatistics> findByDateRange(LocalDate startDate, LocalDate endDate);

    List<PersistedStatistics> findAll();

    List<PersistedStatistics> findLastNDays(int days);

    List<PersistedStatistics> findCurrentMonth();

    PersistedStatistics calculateAggregates(LocalDate startDate, LocalDate endDate);

    int deleteStatisticsOlderThan(LocalDate cutoffDate);

    long count();

    void deleteAll();
}