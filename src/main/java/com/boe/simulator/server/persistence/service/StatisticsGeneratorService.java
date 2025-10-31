package com.boe.simulator.server.persistence.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.persistence.model.PersistedSession;
import com.boe.simulator.server.persistence.model.PersistedStatistics;
import com.boe.simulator.server.persistence.repository.SessionRepository;
import com.boe.simulator.server.persistence.repository.StatisticsRepository;
import com.boe.simulator.server.session.ClientSessionManager;

public class StatisticsGeneratorService {
    private static final Logger LOGGER = Logger.getLogger(StatisticsGeneratorService.class.getName());
    
    private final SessionRepository sessionRepository;
    private final StatisticsRepository statisticsRepository;
    private final ClientSessionManager sessionManager;
    private final ErrorHandler errorHandler;
    
    private final ScheduledExecutorService scheduler;
    private volatile boolean running;
    
    public StatisticsGeneratorService(
            SessionRepository sessionRepository,
            StatisticsRepository statisticsRepository,
            ClientSessionManager sessionManager,
            ErrorHandler errorHandler
    ) {
        this.sessionRepository = sessionRepository;
        this.statisticsRepository = statisticsRepository;
        this.sessionManager = sessionManager;
        this.errorHandler = errorHandler;
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.running = false;
    }

    public void start() {
        if (running) {
            LOGGER.warning("Statistics generator already running");
            return;
        }
        
        running = true;
        
        // Generate statistics for the current day every hour
        scheduler.scheduleAtFixedRate(
            this::generateCurrentDayStatistics,
            1, // inicial delay: 1 minuto
            60, // every 60 minutes
            TimeUnit.MINUTES
        );
        
        // Generate statistics for the previous day at midnight
        scheduler.scheduleAtFixedRate(
            this::generatePreviousDayStatistics,
            calculateInitialDelayToMidnight(),
            24 * 60, // 24 hours
            TimeUnit.MINUTES
        );
        
        LOGGER.info("Statistics generator started (hourly updates + midnight aggregation)");
    }
    

    public void stop() {
        if (!running) return;
        
        running = false;
        scheduler.shutdown();
        
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) scheduler.shutdownNow();
            
            LOGGER.info("Statistics generator stopped");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
            LOGGER.warning("Statistics generator shutdown interrupted");
        }
    }

    public void generateCurrentDayStatistics() {
        try {
            LocalDate today = LocalDate.now();
            LOGGER.log(Level.INFO, "Generating statistics for {0}", today);

            List<PersistedSession> todaySessions = sessionRepository.findByDate(today);
            
            if (todaySessions.isEmpty()) {
                LOGGER.log(Level.INFO, "No sessions found for {0}, skipping", today);
                return;
            }

            PersistedStatistics stats = PersistedStatistics.fromSessions(today, todaySessions);

            if (errorHandler != null) {
                stats = stats.withErrorMetrics(
                    errorHandler.getTotalErrors(),
                    errorHandler.getTotalWarnings()
                );
            }
            
            if (sessionManager != null) {
                // Nota: Necesario agregar un método para trackear el peak
                stats = stats.withPeakConcurrentSessions(
                    sessionManager.getActiveSessionCount()
                );
            }

            statisticsRepository.save(stats);
            
            LOGGER.log(Level.INFO, "✓ Statistics generated: {0}", stats);
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate statistics", e);
        }
    }

    public void generatePreviousDayStatistics() {
        try {
            LocalDate yesterday = LocalDate.now().minusDays(1);
            LOGGER.log(Level.INFO, "Generating final statistics for {0}", yesterday);
            
            List<PersistedSession> yesterdaySessions = sessionRepository.findByDate(yesterday);
            
            if (yesterdaySessions.isEmpty()) {
                LOGGER.log(Level.INFO, "No sessions found for {0}", yesterday);
                return;
            }
            
            PersistedStatistics stats = PersistedStatistics.fromSessions(yesterday, yesterdaySessions);
            
            if (errorHandler != null) {
                stats = stats.withErrorMetrics(
                    errorHandler.getTotalErrors(),
                    errorHandler.getTotalWarnings()
                );
            }
            
            statisticsRepository.save(stats);
            
            LOGGER.log(Level.INFO, "✓ Final statistics for {0}: {1}", new Object[]{yesterday, stats});
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate previous day statistics", e);
        }
    }

    public PersistedStatistics generateStatisticsForDate(LocalDate date) {
        try {
            List<PersistedSession> sessions = sessionRepository.findByDate(date);
            PersistedStatistics stats = PersistedStatistics.fromSessions(date, sessions);
            statisticsRepository.save(stats);
            return stats;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to generate statistics for " + date, e);
            return null;
        }
    }

    public void cleanupOldData(int daysToKeep) {
        try {
            LocalDate cutoffDate = LocalDate.now().minusDays(daysToKeep);
            Instant cutoffInstant = cutoffDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            
            LOGGER.log(Level.INFO, "Cleaning up data older than {0} days (cutoff: {1})", new Object[]{daysToKeep, cutoffDate});
            
            int deletedSessions = sessionRepository.deleteSessionsOlderThan(cutoffInstant);
            int deletedStats = statisticsRepository.deleteStatisticsOlderThan(cutoffDate);
            
            LOGGER.log(Level.INFO, "✓ Cleanup complete: {0} sessions, {1} statistics deleted", new Object[]{deletedSessions, deletedStats});
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to cleanup old data", e);
        }
    }

    private long calculateInitialDelayToMidnight() {
        LocalDate tomorrow = LocalDate.now().plusDays(1);
        Instant midnight = tomorrow.atStartOfDay(ZoneId.systemDefault()).toInstant();
        long minutesUntilMidnight = (midnight.toEpochMilli() - Instant.now().toEpochMilli()) / (60 * 1000);
        return Math.max(1, minutesUntilMidnight);
    }
    
    public boolean isRunning() {
        return running;
    }
}