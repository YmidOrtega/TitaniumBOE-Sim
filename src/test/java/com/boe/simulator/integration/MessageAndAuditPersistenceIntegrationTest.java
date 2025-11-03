package com.boe.simulator.integration;

import com.boe.simulator.protocol.message.BoeMessageFactory;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.model.AuditEvent;
import com.boe.simulator.server.persistence.model.PersistedMessage;
import com.boe.simulator.server.persistence.repository.AuditRepository;
import com.boe.simulator.server.persistence.repository.MessageRepository;
import com.boe.simulator.server.persistence.service.AuditLogger;
import com.boe.simulator.server.persistence.service.AuditRepositoryService;
import com.boe.simulator.server.persistence.service.MessageLogger;
import com.boe.simulator.server.persistence.service.MessageRepositoryService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageAndAuditPersistenceIntegrationTest {

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║  Persistencia Fase 4: Mensajes y Auditoría             ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        try {
            // Test 1: Message Repository
            testMessageRepository();

            // Test 2: Audit Repository
            testAuditRepository();

            // Test 3: Message Logger
            testMessageLogger();

            // Test 4: Audit Logger
            testAuditLogger();

            // Test 5: Advanced Queries
            testAdvancedQueries();

            // Test 6: Data Cleanup
            testDataCleanup();

            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║  ✓ Todos los Tests Pasaron Exitosamente                ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");

        } catch (Exception e) {
            System.err.println("\n✗ Test falló: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Cleanup
            RocksDBManager.getInstance().close();
        }
    }

    private static void testMessageRepository() {
        System.out.println("═══ Test 1: Message Repository ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance("./data/test_phase4");
        MessageRepository messageRepo = new MessageRepositoryService(dbManager);

        // Create test messages
        byte[] testData1 = createTestMessage(BoeMessageFactory.LOGIN_REQUEST);
        byte[] testData2 = createTestMessage(BoeMessageFactory.LOGIN_RESPONSE);
        byte[] testData3 = createTestMessage(BoeMessageFactory.CLIENT_HEARTBEAT);

        PersistedMessage msg1 = PersistedMessage.create(
                PersistedMessage.MessageDirection.INBOUND,
                BoeMessageFactory.LOGIN_REQUEST,
                "LoginRequest",
                1,
                "testuser",
                "TEST1",
                1,
                testData1
        );

        PersistedMessage msg2 = PersistedMessage.create(
                PersistedMessage.MessageDirection.OUTBOUND,
                BoeMessageFactory.LOGIN_RESPONSE,
                "LoginResponse",
                1,
                "testuser",
                "TEST1",
                1,
                testData2
        );

        PersistedMessage msg3 = PersistedMessage.create(
                PersistedMessage.MessageDirection.INBOUND,
                BoeMessageFactory.CLIENT_HEARTBEAT,
                "ClientHeartbeat",
                2,
                "otheruser",
                "TEST2",
                1,
                testData3
        );

        // Save messages
        messageRepo.save(msg1);
        messageRepo.save(msg2);
        messageRepo.save(msg3);
        System.out.println("✓ Guardados 3 mensajes");

        // Test retrieval
        long count = messageRepo.count();
        System.out.println("✓ Total mensajes: " + count);
        assert count >= 3 : "Should have at least 3 messages";

        // Test find by username
        List<PersistedMessage> userMessages = messageRepo.findByUsername("testuser");
        System.out.println("✓ Mensajes de 'testuser': " + userMessages.size());
        assert userMessages.size() >= 2 : "Should have at least 2 messages for testuser";

        // Test find by type
        List<PersistedMessage> loginMessages = messageRepo.findByMessageType(BoeMessageFactory.LOGIN_REQUEST);
        System.out.println("✓ Mensajes LoginRequest: " + loginMessages.size());
        assert !loginMessages.isEmpty() : "Should have login messages";

        // Test find by direction
        List<PersistedMessage> inbound = messageRepo.findByDirection(PersistedMessage.MessageDirection.INBOUND);
        System.out.println("✓ Mensajes inbound: " + inbound.size());
        assert inbound.size() >= 2 : "Should have at least 2 inbound messages";

        // Test find latest
        List<PersistedMessage> latest = messageRepo.findLatest(2);
        System.out.println("✓ Últimos 2 mensajes recuperados");
        assert latest.size() <= 2 : "Should return at most 2 messages";

        System.out.println();
    }

    private static void testAuditRepository() {
        System.out.println("═══ Test 2: Audit Repository ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance();
        AuditRepository auditRepo = new AuditRepositoryService(dbManager);

        // Create test events
        Map<String, String> details1 = new HashMap<>();
        details1.put("ip_address", "192.168.1.100");
        details1.put("port", "8080");

        AuditEvent event1 = AuditEvent.create(
                AuditEvent.EventType.LOGIN_SUCCESS,
                AuditEvent.EventSeverity.INFO,
                1,
                "testuser",
                "TEST1",
                "User logged in successfully",
                details1
        );

        AuditEvent event2 = AuditEvent.create(
                AuditEvent.EventType.LOGIN_FAILURE,
                AuditEvent.EventSeverity.WARNING,
                2,
                "baduser",
                "TEST2",
                "Invalid credentials",
                new HashMap<>()
        );

        AuditEvent event3 = AuditEvent.create(
                AuditEvent.EventType.RATE_LIMIT_EXCEEDED,
                AuditEvent.EventSeverity.WARNING,
                1,
                "testuser",
                "TEST1",
                "Rate limit exceeded",
                new HashMap<>()
        );

        // Save events
        auditRepo.save(event1);
        auditRepo.save(event2);
        auditRepo.save(event3);
        System.out.println("✓ Guardados 3 eventos de auditoría");

        // Test retrieval
        long count = auditRepo.count();
        System.out.println("✓ Total eventos: " + count);
        assert count >= 3 : "Should have at least 3 events";

        // Test find by type
        List<AuditEvent> loginEvents = auditRepo.findByType(AuditEvent.EventType.LOGIN_SUCCESS);
        System.out.println("✓ Eventos LOGIN_SUCCESS: " + loginEvents.size());
        assert !loginEvents.isEmpty() : "Should have login success events";

        // Test find by severity
        List<AuditEvent> warnings = auditRepo.findBySeverity(AuditEvent.EventSeverity.WARNING);
        System.out.println("✓ Eventos WARNING: " + warnings.size());
        assert warnings.size() >= 2 : "Should have at least 2 warnings";

        // Test find by username
        List<AuditEvent> userEvents = auditRepo.findByUsername("testuser");
        System.out.println("✓ Eventos de 'testuser': " + userEvents.size());
        assert userEvents.size() >= 2 : "Should have at least 2 events for testuser";

        // Test find latest
        List<AuditEvent> latest = auditRepo.findLatest(2);
        System.out.println("✓ Últimos 2 eventos recuperados");
        assert latest.size() <= 2 : "Should return at most 2 events";

        System.out.println();
    }

    private static void testMessageLogger() {
        System.out.println("═══ Test 3: Message Logger ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance();
        MessageRepository messageRepo = new MessageRepositoryService(dbManager);
        MessageLogger logger = new MessageLogger(messageRepo);

        // Get statistics
        MessageLogger.MessageStatistics stats = logger.getStatistics();
        System.out.println("✓ Estadísticas de mensajes:");
        System.out.println("  Total: " + stats.totalMessages());
        System.out.println("  Inbound: " + stats.inboundMessages());
        System.out.println("  Outbound: " + stats.outboundMessages());

        assert stats.totalMessages() > 0 : "Should have messages";
        assert stats.inboundMessages() > 0 : "Should have inbound messages";
        assert stats.outboundMessages() > 0 : "Should have outbound messages";

        System.out.println();
    }

    private static void testAuditLogger() {
        System.out.println("═══ Test 4: Audit Logger ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance();
        AuditRepository auditRepo = new AuditRepositoryService(dbManager);
        AuditLogger logger = new AuditLogger(auditRepo);

        // Log various events
        logger.logConnection(AuditEvent.EventType.CONNECTION_ACCEPTED, 100, "Test connection");
        System.out.println("✓ Logged connection event");

        logger.logAuthentication(
                AuditEvent.EventType.LOGIN_SUCCESS,
                100,
                "loggertest",
                "LT1",
                true,
                "Successful authentication"
        );
        System.out.println("✓ Logged authentication event");

        Map<String, String> secDetails = new HashMap<>();
        secDetails.put("attempts", "5");
        logger.logSecurity(
                AuditEvent.EventType.RATE_LIMIT_EXCEEDED,
                100,
                "loggertest",
                "Rate limit hit",
                secDetails
        );
        System.out.println("✓ Logged security event");

        logger.logSystem(
                AuditEvent.EventType.SERVER_STARTED,
                AuditEvent.EventSeverity.INFO,
                "Server started successfully"
        );
        System.out.println("✓ Logged system event");

        // Get statistics
        AuditLogger.AuditStatistics stats = logger.getStatistics();
        System.out.println("✓ Estadísticas de auditoría:");
        System.out.println("  Total: " + stats.totalEvents());
        System.out.println("  Critical: " + stats.criticalEvents());
        System.out.println("  Errors: " + stats.errorEvents());
        System.out.println("  Warnings: " + stats.warningEvents());

        assert stats.totalEvents() > 0 : "Should have audit events";

        System.out.println();
    }

    private static void testAdvancedQueries() {
        System.out.println("═══ Test 5: Advanced Queries ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance();
        MessageRepository messageRepo = new MessageRepositoryService(dbManager);
        AuditRepository auditRepo = new AuditRepositoryService(dbManager);

        // Test message search with criteria
        Instant now = Instant.now();
        Instant oneHourAgo = now.minus(1, ChronoUnit.HOURS);

        MessageRepository.MessageSearchCriteria msgCriteria =
                MessageRepository.MessageSearchCriteria.builder()
                        .username("testuser")
                        .direction(PersistedMessage.MessageDirection.INBOUND)
                        .startDate(oneHourAgo)
                        .limit(10)
                        .build();

        List<PersistedMessage> searchResults = messageRepo.search(msgCriteria);
        System.out.println("✓ Búsqueda de mensajes: " + searchResults.size() + " resultados");

        // Test audit search with criteria
        AuditRepository.AuditSearchCriteria auditCriteria =
                AuditRepository.AuditSearchCriteria.builder()
                        .severity(AuditEvent.EventSeverity.WARNING)
                        .startDate(oneHourAgo)
                        .limit(10)
                        .build();

        List<AuditEvent> auditResults = auditRepo.search(auditCriteria);
        System.out.println("✓ Búsqueda de auditoría: " + auditResults.size() + " resultados");

        // Test date range queries
        List<PersistedMessage> recentMessages = messageRepo.findByDateRange(oneHourAgo, now);
        System.out.println("✓ Mensajes recientes (última hora): " + recentMessages.size());

        List<AuditEvent> recentEvents = auditRepo.findByDateRange(oneHourAgo, now);
        System.out.println("✓ Eventos recientes (última hora): " + recentEvents.size());

        System.out.println();
    }

    private static void testDataCleanup() {
        System.out.println("═══ Test 6: Data Cleanup ═══");

        RocksDBManager dbManager = RocksDBManager.getInstance();
        MessageRepository messageRepo = new MessageRepositoryService(dbManager);
        AuditRepository auditRepo = new AuditRepositoryService(dbManager);

        // Test delete old data
        Instant cutoffDate = Instant.now().minus(365, ChronoUnit.DAYS);

        int deletedMessages = messageRepo.deleteOlderThan(cutoffDate);
        System.out.println("✓ Mensajes eliminados (>1 año): " + deletedMessages);

        int deletedEvents = auditRepo.deleteOlderThan(cutoffDate);
        System.out.println("✓ Eventos eliminados (>1 año): " + deletedEvents);

        // Verify counts
        long remainingMessages = messageRepo.count();
        long remainingEvents = auditRepo.count();

        System.out.println("✓ Mensajes restantes: " + remainingMessages);
        System.out.println("✓ Eventos restantes: " + remainingEvents);

        System.out.println();
    }

    private static byte[] createTestMessage(byte messageType) {
        // Create a minimal BOE message
        byte[] message = new byte[10];
        message[0] = (byte) 0xBA; // Start marker
        message[1] = (byte) 0xBA;
        message[2] = 0x08; // Message length (little endian)
        message[3] = 0x00;
        message[4] = messageType; // Message type
        message[5] = 0x00; // Matching unit
        message[6] = 0x01; // Sequence number (little endian)
        message[7] = 0x00;
        message[8] = 0x00;
        message[9] = 0x00;
        return message;
    }
}