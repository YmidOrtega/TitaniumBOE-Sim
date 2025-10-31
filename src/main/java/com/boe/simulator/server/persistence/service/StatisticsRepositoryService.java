package com.boe.simulator.server.persistence.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.rocksdb.RocksDBException;

import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.PersistedStatistics;
import com.boe.simulator.server.persistence.repository.StatisticsRepository;
import com.boe.simulator.server.persistence.util.SerializationUtil;

public class StatisticsRepositoryService implements StatisticsRepository {
    private static final Logger LOGGER = Logger.getLogger(StatisticsRepositoryService.class.getName());
    
    private final RocksDBManager dbManager;
    private final SerializationUtil serializer;

    public StatisticsRepositoryService(RocksDBManager dbManager) {
        this.dbManager = dbManager;
        this.serializer = SerializationUtil.getInstance();
    }

    @Override
    public void save(PersistedStatistics statistics) {
        try {
            String key = statistics.getStorageKey();
            byte[] value = serializer.serialize(statistics);
            dbManager.put(RocksDBManager.CF_CONFIG, key.getBytes(), value);
            
            LOGGER.log(Level.FINE, "Saved statistics for date: {0}", statistics.date());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to save statistics for: " + statistics.date(), e);
            throw new RuntimeException("Failed to save statistics", e);
        }
    }

    @Override
    public Optional<PersistedStatistics> findByDate(LocalDate date) {
        try {
            String key = "stats:" + date.toString();
            byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, key.getBytes());
            
            if (data == null) return Optional.empty();
            
            PersistedStatistics stats = serializer.deserialize(data, PersistedStatistics.class);
            return Optional.of(stats);
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find statistics for date: " + date, e);
            return Optional.empty();
        }
    }

    @Override
    public List<PersistedStatistics> findByDateRange(LocalDate startDate, LocalDate endDate) {
        try {
            List<PersistedStatistics> allStats = findAll();
            return allStats.stream()
                    .filter(s -> !s.date().isBefore(startDate) && !s.date().isAfter(endDate))
                    .sorted(Comparator.comparing(PersistedStatistics::date))
                    .toList();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to find statistics by date range", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedStatistics> findAll() {
        try {
            List<PersistedStatistics> statistics = new ArrayList<>();
            String prefix = "stats:";
            List<byte[]> keys = dbManager.getKeysWithPrefix(RocksDBManager.CF_CONFIG, prefix.getBytes());
            
            for (byte[] keyBytes : keys) {
                byte[] data = dbManager.get(RocksDBManager.CF_CONFIG, keyBytes);
                if (data != null) {
                    PersistedStatistics stats = serializer.deserialize(data, PersistedStatistics.class);
                    statistics.add(stats);
                }
            }
            
            return statistics.stream()
                    .sorted(Comparator.comparing(PersistedStatistics::date))
                    .toList();
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to find all statistics", e);
            return new ArrayList<>();
        }
    }

    @Override
    public List<PersistedStatistics> findLastNDays(int days) {
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusDays(days - 1);
        return findByDateRange(startDate, endDate);
    }

    @Override
    public List<PersistedStatistics> findCurrentMonth() {
        LocalDate now = LocalDate.now();
        LocalDate startOfMonth = now.withDayOfMonth(1);
        return findByDateRange(startOfMonth, now);
    }

    @Override
    public PersistedStatistics calculateAggregates(LocalDate startDate, LocalDate endDate) {
        try {
            List<PersistedStatistics> statsInRange = findByDateRange(startDate, endDate);
            
            if (statsInRange.isEmpty()) return PersistedStatistics.createForDate(startDate);
            
            long totalSessions = statsInRange.stream().mapToLong(PersistedStatistics::totalSessions).sum();
            long successfulLogins = statsInRange.stream().mapToLong(PersistedStatistics::successfulLogins).sum();
            long failedLogins = statsInRange.stream().mapToLong(PersistedStatistics::failedLogins).sum();
            
            long uniqueUsers = statsInRange.stream().mapToLong(PersistedStatistics::uniqueUsers).max().orElse(0);
            
            long totalMessagesRx = statsInRange.stream().mapToLong(PersistedStatistics::totalMessagesReceived).sum();
            long totalMessagesTx = statsInRange.stream().mapToLong(PersistedStatistics::totalMessagesSent).sum();
            long totalHeartbeatsRx = statsInRange.stream().mapToLong(PersistedStatistics::totalHeartbeatsReceived).sum();
            long totalHeartbeatsTx = statsInRange.stream().mapToLong(PersistedStatistics::totalHeartbeatsSent).sum();
            
            long avgDuration = (long) statsInRange.stream()
                    .mapToLong(PersistedStatistics::averageSessionDurationSeconds)
                    .average()
                    .orElse(0.0);
            
            int peakConcurrent = statsInRange.stream().mapToInt(PersistedStatistics::peakConcurrentSessions).max().orElse(0);
            int totalErrors = statsInRange.stream().mapToInt(PersistedStatistics::totalErrors).sum();
            int totalWarnings = statsInRange.stream().mapToInt(PersistedStatistics::totalWarnings).sum();
            
            return new PersistedStatistics(
                    startDate,
                    java.time.Instant.now(),
                    totalSessions,
                    successfulLogins,
                    failedLogins,
                    uniqueUsers,
                    totalMessagesRx,
                    totalMessagesTx,
                    totalHeartbeatsRx,
                    totalHeartbeatsTx,
                    avgDuration,
                    peakConcurrent,
                    totalErrors,
                    totalWarnings
            );
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to calculate aggregates", e);
            return PersistedStatistics.createForDate(startDate);
        }
    }

    @Override
    public int deleteStatisticsOlderThan(LocalDate cutoffDate) {
        try {
            List<PersistedStatistics> oldStats = findAll().stream()
                    .filter(s -> s.date().isBefore(cutoffDate))
                    .toList();
            
            int deleted = 0;
            for (PersistedStatistics stats : oldStats) {
                String key = stats.getStorageKey();
                dbManager.delete(RocksDBManager.CF_CONFIG, key.getBytes());
                deleted++;
            }
            
            LOGGER.log(Level.INFO, "Deleted {0} statistics older than {1}", new Object[]{deleted, cutoffDate});
            return deleted;
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete old statistics", e);
            return 0;
        }
    }

    @Override
    public long count() {
        return findAll().size();
    }

    @Override
    public void deleteAll() {
        try {
            List<PersistedStatistics> allStats = findAll();
            for (PersistedStatistics stats : allStats) {
                String key = stats.getStorageKey();
                dbManager.delete(RocksDBManager.CF_CONFIG, key.getBytes());
            }
            LOGGER.info("Deleted all statistics");
        } catch (RocksDBException e) {
            LOGGER.log(Level.SEVERE, "Failed to delete all statistics", e);
            throw new RuntimeException("Failed to delete all statistics", e);
        }
    }
}