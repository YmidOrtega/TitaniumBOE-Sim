package com.boe.simulator.integration;

import java.time.LocalDate;
import java.util.logging.Level;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.server.CboeServer;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.persistence.RocksDBManager;
import com.boe.simulator.server.persistence.repository.SessionRepository;
import com.boe.simulator.server.persistence.repository.StatisticsRepository;
import com.boe.simulator.server.persistence.service.SessionRepositoryService;
import com.boe.simulator.server.persistence.service.StatisticsRepositoryService;

public class FullPersistenceIntegrationTest {
    
    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Integración Completa - Persistencia Fase 2    ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        CboeServer server = null;
        
        try {
            // 1. Iniciar servidor con persistencia
            System.out.println("═══ Paso 1: Iniciando Servidor ═══");
            ServerConfiguration config = ServerConfiguration.builder()
                    .port(8081)
                    .logLevel(Level.INFO)
                    .build();
            
            server = new CboeServer(config);
            server.start();
            System.out.println("✓ Servidor iniciado en puerto 8081\n");
            
            Thread.sleep(2000);
            
            // 2. Crear múltiples sesiones
            System.out.println("═══ Paso 2: Creando Sesiones ═══");
            
            // Sesión exitosa 1
            System.out.println("\n--- Cliente 1: Login Exitoso ---");
            BoeConnectionHandler client1 = createTestClient("Client1", "localhost", 8081);
            client1.connect().get();
            client1.startListener();
            Thread.sleep(300);
            
            LoginRequestMessage login1 = new LoginRequestMessage("USER", "PASS", "TEST1");
            client1.sendMessageRaw(login1.toBytes()).get();
            Thread.sleep(1000);
            
            // Sesión exitosa 2
            System.out.println("\n--- Cliente 2: Login Exitoso ---");
            BoeConnectionHandler client2 = createTestClient("Client2", "localhost", 8081);
            client2.connect().get();
            client2.startListener();
            Thread.sleep(300);
            
            LoginRequestMessage login2 = new LoginRequestMessage("TEST", "TEST", "TEST2");
            client2.sendMessageRaw(login2.toBytes()).get();
            Thread.sleep(1000);
            
            // Sesión fallida (password incorrecto)
            System.out.println("\n--- Cliente 3: Login Fallido ---");
            BoeConnectionHandler client3 = createTestClient("Client3", "localhost", 8081);
            client3.connect().get();
            client3.startListener();
            Thread.sleep(300);
            
            LoginRequestMessage login3 = new LoginRequestMessage("USER", "WRONG", "TEST3");
            client3.sendMessageRaw(login3.toBytes()).get();
            Thread.sleep(1000);
            
            System.out.println("\n✓ Sesiones creadas (2 exitosas, 1 fallida)");
            
            // 3. Mantener sesiones activas un tiempo
            System.out.println("\n═══ Paso 3: Sesiones Activas ═══");
            System.out.println("Esperando 5 segundos...");
            Thread.sleep(5000);
            
            // 4. Cerrar sesiones
            System.out.println("\n═══ Paso 4: Cerrando Sesiones ═══");
            
            LogoutRequestMessage logout1 = new LogoutRequestMessage();
            client1.sendMessageRaw(logout1.toBytes()).get();
            Thread.sleep(500);
            client1.stopListener();
            client1.disconnect().get();
            System.out.println("✓ Cliente 1 desconectado");
            
            LogoutRequestMessage logout2 = new LogoutRequestMessage();
            client2.sendMessageRaw(logout2.toBytes()).get();
            Thread.sleep(500);
            client2.stopListener();
            client2.disconnect().get();
            System.out.println("✓ Cliente 2 desconectado");
            
            client3.stopListener();
            client3.disconnect().get();
            System.out.println("✓ Cliente 3 desconectado");
            
            Thread.sleep(2000);
            
            // 5. Verificar persistencia
            System.out.println("\n═══ Paso 5: Verificando Persistencia ═══");
            RocksDBManager dbManager = server.getDatabaseManager();
            SessionRepository sessionRepo = new SessionRepositoryService(dbManager);
            StatisticsRepository statsRepo = new StatisticsRepositoryService(dbManager);
            
            long totalSessions = sessionRepo.count();
            long successfulLogins = sessionRepo.countSuccessfulLogins();
            long failedLogins = sessionRepo.countFailedLogins();
            
            System.out.println("\n📊 Sesiones Persistidas:");
            System.out.println("  - Total sesiones: " + totalSessions);
            System.out.println("  - Logins exitosos: " + successfulLogins);
            System.out.println("  - Logins fallidos: " + failedLogins);
            
            assert totalSessions >= 3 : "Should have at least 3 sessions";
            assert successfulLogins >= 2 : "Should have at least 2 successful logins";
            assert failedLogins >= 1 : "Should have at least 1 failed login";
            
            // Verificar sesiones por usuario
            var userSessions = sessionRepo.findByUsername("USER");
            System.out.println("\n  - Sesiones de 'USER': " + userSessions.size());
            for (var session : userSessions) {
                System.out.println("    • " + session.sessionId() + 
                                " - Exitoso: " + session.loginSuccessful() +
                                " - Duración: " + session.durationSeconds() + "s" +
                                " - Mensajes: " + session.messagesReceived() + "/" + session.messagesSent());
            }
            
            // 6. Generar estadísticas manualmente
            System.out.println("\n═══ Paso 6: Generando Estadísticas ═══");
            server.getSessionManager().getSessionRepository();
            
            // Forzar generación de estadísticas
            var statsGenerator = new com.boe.simulator.server.persistence.service.StatisticsGeneratorService(
                sessionRepo,
                statsRepo,
                server.getSessionManager(),
                server.getErrorHandler()
            );
            
            statsGenerator.generateCurrentDayStatistics();
            System.out.println("✓ Estadísticas generadas");
            
            // Verificar estadísticas
            LocalDate today = LocalDate.now();
            var todayStats = statsRepo.findByDate(today);
            
            if (todayStats.isPresent()) {
                var stats = todayStats.get();
                System.out.println("\n📈 Estadísticas del Día:");
                System.out.println("  - Fecha: " + stats.date());
                System.out.println("  - Total sesiones: " + stats.totalSessions());
                System.out.println("  - Tasa de éxito: " + String.format("%.1f%%", stats.getLoginSuccessRate()));
                System.out.println("  - Usuarios únicos: " + stats.uniqueUsers());
                System.out.println("  - Duración promedio: " + stats.averageSessionDurationSeconds() + "s");
                System.out.println("  - Mensajes Rx/Tx: " + stats.totalMessagesReceived() + "/" + stats.totalMessagesSent());
            }
            
            // 7. Apagar servidor
            System.out.println("\n═══ Paso 7: Apagando Servidor ═══");
            server.shutdown();
            Thread.sleep(1000);
            System.out.println("✓ Servidor apagado");
            
            // 8. Verificar persistencia después de reinicio
            System.out.println("\n═══ Paso 8: Verificando Persistencia Después de Reinicio ═══");
            RocksDBManager newDbManager = RocksDBManager.getInstance();
            SessionRepository newSessionRepo = new SessionRepositoryService(newDbManager);
            
            long persistedSessions = newSessionRepo.count();
            System.out.println("✓ Sesiones recuperadas después de reinicio: " + persistedSessions);
            
            assert persistedSessions >= totalSessions : "Sessions should persist after restart";
            
            System.out.println("\n╔════════════════════════════════════════════════════════╗");
            System.out.println("║    ✓✓✓ Test Completado Exitosamente ✓✓✓               ║");
            System.out.println("╚════════════════════════════════════════════════════════╝");
            
        } catch (Exception e) {
            System.err.println("\n✗✗✗ Test Falló: " + e.getMessage());
        } finally {
            if (server != null && server.isRunning()) server.shutdown();
            
            try {
                Thread.sleep(1000);
                RocksDBManager.getInstance().close();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
    
    private static BoeConnectionHandler createTestClient(String name, String host, int port) {
        BoeConnectionHandler client = new BoeConnectionHandler(host, port);
        
        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                char status = (char) response.getLoginResponseStatus();
                System.out.println("  " + name + " - Login: " + status + " - " + response.getLoginResponseText());
            }
        });
        
        return client;
    }
}
