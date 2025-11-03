package com.boe.simulator.server.persistence.service;

import com.boe.simulator.server.persistence.model.AuditEvent;
import com.boe.simulator.server.persistence.repository.AuditRepository;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

public class AuditLogger {
    private static final Logger LOGGER = Logger.getLogger(AuditLogger.class.getName());

    private final AuditRepository auditRepository;
    private final boolean enabled;

    public AuditLogger(AuditRepository auditRepository, boolean enabled) {
        this.auditRepository = auditRepository;
        this.enabled = enabled;
    }

    public AuditLogger(AuditRepository auditRepository) {
        this(auditRepository, true);
    }

    public void logConnection(AuditEvent.EventType eventType, int connectionId, String description) {
        if (!enabled) return;

        AuditEvent event = AuditEvent.create(
                eventType,
                AuditEvent.EventSeverity.INFO,
                connectionId,
                null,
                null,
                description,
                new HashMap<>()
        );

        auditRepository.save(event);
        LOGGER.fine("Logged connection event: " + eventType);
    }

    public void logAuthentication(
            AuditEvent.EventType eventType,
            int connectionId,
            String username,
            String sessionSubID,
            boolean success,
            String reason
    ) {
        if (!enabled) return;

        AuditEvent.EventSeverity severity = success ?
                AuditEvent.EventSeverity.INFO :
                AuditEvent.EventSeverity.WARNING;

        Map<String, String> details = new HashMap<>();
        details.put("success", String.valueOf(success));
        details.put("reason", reason);

        AuditEvent event = AuditEvent.create(
                eventType,
                severity,
                connectionId,
                username,
                sessionSubID,
                reason,
                details
        );

        auditRepository.save(event);
        LOGGER.fine("Logged authentication event: " + eventType);
    }

    public void logSecurity(
            AuditEvent.EventType eventType,
            int connectionId,
            String username,
            String description,
            Map<String, String> details
    ) {
        if (!enabled) return;

        AuditEvent event = AuditEvent.create(
                eventType,
                AuditEvent.EventSeverity.WARNING,
                connectionId,
                username,
                null,
                description,
                details
        );

        auditRepository.save(event);
        LOGGER.warning("Security event: " + description);
    }

    public void logSystem(
            AuditEvent.EventType eventType,
            AuditEvent.EventSeverity severity,
            String description
    ) {
        if (!enabled) return;

        AuditEvent event = AuditEvent.create(
                eventType,
                severity,
                description
        );

        auditRepository.save(event);
        LOGGER.info("System event: " + description);
    }

    public void logError(
            int connectionId,
            String username,
            String description,
            Throwable error
    ) {
        if (!enabled) return;

        Map<String, String> details = new HashMap<>();
        details.put("error_type", error.getClass().getSimpleName());
        details.put("error_message", error.getMessage());

        AuditEvent event = AuditEvent.create(
                AuditEvent.EventType.ERROR_OCCURRED,
                AuditEvent.EventSeverity.ERROR,
                connectionId,
                username,
                null,
                description,
                details
        );

        auditRepository.save(event);
        LOGGER.severe("Error event: " + description);
    }

    public AuditStatistics getStatistics() {
        long totalEvents = auditRepository.count();
        long criticalEvents = auditRepository.countBySeverity(AuditEvent.EventSeverity.CRITICAL);
        long errorEvents = auditRepository.countBySeverity(AuditEvent.EventSeverity.ERROR);
        long warningEvents = auditRepository.countBySeverity(AuditEvent.EventSeverity.WARNING);

        return new AuditStatistics(totalEvents, criticalEvents, errorEvents, warningEvents);
    }

    public record AuditStatistics(
            long totalEvents,
            long criticalEvents,
            long errorEvents,
            long warningEvents
    ) {}
}