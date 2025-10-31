package com.boe.simulator.server.persistence.model;

import java.time.Instant;
import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PersistedStatistics(
        @JsonProperty("date") LocalDate date,
        @JsonProperty("timestamp") Instant timestamp,
        @JsonProperty("totalSessions") long totalSessions,
        @JsonProperty("successfulLogins") long successfulLogins,
        @JsonProperty("failedLogins") long failedLogins,
        @JsonProperty("uniqueUsers") long uniqueUsers,
        @JsonProperty("totalMessagesReceived") long totalMessagesReceived,
        @JsonProperty("totalMessagesSent") long totalMessagesSent,
        @JsonProperty("totalHeartbeatsReceived") long totalHeartbeatsReceived,
        @JsonProperty("totalHeartbeatsSent") long totalHeartbeatsSent,
        @JsonProperty("averageSessionDurationSeconds") long averageSessionDurationSeconds,
        @JsonProperty("peakConcurrentSessions") int peakConcurrentSessions,
        @JsonProperty("totalErrors") int totalErrors,
        @JsonProperty("totalWarnings") int totalWarnings
) {

    @JsonCreator
    public PersistedStatistics {
        if (date == null) throw new IllegalArgumentException("Date cannot be null");
        if (timestamp == null) timestamp = Instant.now();
        
    }

    public static PersistedStatistics createForDate(LocalDate date) {
        return new PersistedStatistics(
                date,
                Instant.now(),
                0, 0, 0, 0,
                0, 0, 0, 0,
                0, 0, 0, 0
        );
    }

    public static PersistedStatistics fromSessions(LocalDate date, java.util.List<PersistedSession> sessions) {
        if (sessions.isEmpty()) return createForDate(date);

        long totalSessions = sessions.size();
        long successfulLogins = sessions.stream().filter(PersistedSession::loginSuccessful).count();
        long failedLogins = totalSessions - successfulLogins;
        long uniqueUsers = sessions.stream().map(PersistedSession::username).distinct().count();
        
        long totalMessagesRx = sessions.stream().mapToLong(PersistedSession::messagesReceived).sum();
        long totalMessagesTx = sessions.stream().mapToLong(PersistedSession::messagesSent).sum();
        long totalHeartbeatsRx = sessions.stream().mapToLong(PersistedSession::heartbeatsReceived).sum();
        long totalHeartbeatsTx = sessions.stream().mapToLong(PersistedSession::heartbeatsSent).sum();
        
        long avgDuration = (long) sessions.stream()
                .filter(s -> !s.isActive())
                .mapToLong(PersistedSession::durationSeconds)
                .average()
                .orElse(0.0);

        return new PersistedStatistics(
                date,
                Instant.now(),
                totalSessions,
                successfulLogins,
                failedLogins,
                uniqueUsers,
                totalMessagesRx,
                totalMessagesTx,
                totalHeartbeatsRx,
                totalHeartbeatsTx,
                avgDuration,
                0,
                0, 0
        );
    }

    public PersistedStatistics withErrorMetrics(int totalErrors, int totalWarnings) {
        return new PersistedStatistics(
                date,
                timestamp,
                totalSessions,
                successfulLogins,
                failedLogins,
                uniqueUsers,
                totalMessagesReceived,
                totalMessagesSent,
                totalHeartbeatsReceived,
                totalHeartbeatsSent,
                averageSessionDurationSeconds,
                peakConcurrentSessions,
                totalErrors,
                totalWarnings
        );
    }

    public PersistedStatistics withPeakConcurrentSessions(int peak) {
        return new PersistedStatistics(
                date,
                timestamp,
                totalSessions,
                successfulLogins,
                failedLogins,
                uniqueUsers,
                totalMessagesReceived,
                totalMessagesSent,
                totalHeartbeatsReceived,
                totalHeartbeatsSent,
                averageSessionDurationSeconds,
                peak,
                totalErrors,
                totalWarnings
        );
    }

    public double getLoginSuccessRate() {
        if (totalSessions == 0) return 0.0;
        return (double) successfulLogins / totalSessions * 100.0;
    }

    public double getAverageMessagesPerSession() {
        if (totalSessions == 0) return 0.0;
        return (double) (totalMessagesReceived + totalMessagesSent) / totalSessions;
    }

    public String getStorageKey() {
        return "stats:" + date.toString();
    }

    @Override
    public String toString() {
        return "Statistics{" +
                "date=" + date +
                ", sessions=" + totalSessions +
                ", successRate=" + String.format("%.1f%%", getLoginSuccessRate()) +
                ", users=" + uniqueUsers +
                ", avgDuration=" + averageSessionDurationSeconds + "s" +
                ", msgRx=" + totalMessagesReceived +
                ", msgTx=" + totalMessagesSent +
                '}';
    }
}