package com.boe.simulator.integration;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.config.BoeClientConfiguration;
import com.boe.simulator.client.session.ClientSessionState;
import com.boe.simulator.client.session.SessionEventListener;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;

public class FullClientLifecycleIntegrationTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Cliente Completo - TODAS LAS FASES             ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");
        
        // Test 1: Cliente completo con todas las características
        testFullClient();
        
        Thread.sleep(2000);
        
        // Test 2: Auto-reconnect (necesitas detener el servidor manualmente)
        // testAutoReconnect();
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║       Test Completado                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }
    
    /**
     * Test 1: Cliente completo con todas las características
     */
    private static void testFullClient() throws Exception {
        System.out.println("═══ Test: Cliente Completo con Todas las Características ═══\n");
        
        // Configuración completa
        BoeClientConfiguration config = BoeClientConfiguration.builder()
            .host("localhost")
            .port(8080)
            .credentials("USER", "PASS")
            .sessionSubID("FNL1")
            .heartbeatInterval(10)
            .autoHeartbeat(true)        // ✓ Heartbeat automático
            .autoReconnect(true)        // ✓ Reconexión automática
            .maxReconnectAttempts(3)
            .reconnectDelay(5)
            .build();
        
        System.out.println("Configuración del cliente:");
        System.out.println("  Host: " + config.getHost() + ":" + config.getPort());
        System.out.println("  Usuario: " + config.getUsername());
        System.out.println("  Auto-heartbeat: " + config.isAutoHeartbeat());
        System.out.println("  Auto-reconnect: " + config.isAutoReconnect());
        System.out.println("  Max intentos reconexión: " + config.getMaxReconnectAttempts());
        System.out.println();
        
        BoeClient client = new BoeClient(config);
        
        // Setup listeners completos
        client.setSessionListener(new SessionEventListener() {
            @Override
            public void onConnected(String host, int port) {
                System.out.println("✅ Conectado a " + host + ":" + port);
            }
            
            @Override
            public void onLoginSuccess(LoginResponseMessage response) {
                System.out.println("✅ Login exitoso: " + response.getLoginResponseText());
                System.out.println("   Sequence: " + response.getSequenceNumber());
                System.out.println("   Units: " + response.getNumberOfUnits());
            }
            
            @Override
            public void onLoginFailed(LoginResponseMessage response) {
                System.out.println("❌ Login fallido: " + response.getLoginResponseText());
            }
            
            @Override
            public void onLogoutCompleted(LogoutResponseMessage response) {
                System.out.println("✅ Logout completado: " + response.getLogoutReasonText());
            }
            
            @Override
            public void onStateChanged(ClientSessionState oldState, ClientSessionState newState) {
                System.out.println("🔄 Estado: " + oldState + " → " + newState);
            }
            
            @Override
            public void onDisconnected(String reason) {
                System.out.println("📴 Desconectado: " + reason);
            }
            
            @Override
            public void onError(String context, Throwable error) {
                System.out.println("⚠️  Error en " + context + ": " + error.getMessage());
            }
            
            @Override
            public void onReconnecting(int attemptNumber) {
                System.out.println("🔁 Reconectando (intento " + attemptNumber + ")...");
            }
        });
        
        // Conectar
        System.out.println("Conectando...\n");
        client.connect().get();
        
        System.out.println("\n✓ Cliente conectado y autenticado");
        System.out.println("  Estado: " + client.getState());
        System.out.println("  ¿Conectado? " + client.isConnected());
        System.out.println("  ¿Autenticado? " + client.isAuthenticated());
        System.out.println("  Heartbeat activo: " + client.getHeartbeatManager().isActive());
        
        // Observar durante 20 segundos
        System.out.println("\nObservando cliente durante 20 segundos...\n");
        
        for (int i = 1; i <= 4; i++) {
            Thread.sleep(5000);
            System.out.println("[" + (i * 5) + "s] Estado: " + client.getState() + " | Heartbeat: " + client.getHeartbeatManager().isActive());
        }
        
        // Desconectar limpiamente
        System.out.println("\nDesconectando...\n");
        client.disconnect().get();
        
        System.out.println("\n✓ Test completado exitosamente");
        System.out.println("  Estado final: " + client.getState());
    }
    /**
     * Test 2: Auto-reconnect (necesitas detener el servidor manualmente)
     */
    @SuppressWarnings("unused")
    private static void testAutoReconnect() throws Exception {
        System.out.println("═══ Test: Reconexión Automática ═══\n");
        System.out.println("INSTRUCCIONES:");
        System.out.println("1. Asegúrate de que el servidor esté corriendo");
        System.out.println("2. El cliente se conectará");
        System.out.println("3. DETÉN el servidor manualmente");
        System.out.println("4. El cliente detectará la pérdida de conexión");
        System.out.println("5. REINICIA el servidor");
        System.out.println("6. El cliente se reconectará automáticamente\n");
        
        BoeClientConfiguration config = BoeClientConfiguration.builder()
            .host("localhost")
            .port(8080)
            .credentials("TEST", "TEST")
            .sessionSubID("REC1")
            .heartbeatInterval(5)       // Heartbeat cada 5s (más rápido para testing)
            .autoHeartbeat(true)
            .autoReconnect(true)        // ✓ Reconexión habilitada
            .maxReconnectAttempts(5)
            .reconnectDelay(3)          // Esperar 3s entre intentos
            .build();
        
        BoeClient client = new BoeClient(config);
        
        client.setSessionListener(new SessionEventListener() {
            @Override
            public void onConnected(String host, int port) {
                System.out.println("✅ [" + currentTime() + "] Conectado");
            }
            
            @Override
            public void onLoginSuccess(LoginResponseMessage response) {
                System.out.println("✅ [" + currentTime() + "] Login exitoso");
            }
            
            @Override
            public void onStateChanged(ClientSessionState oldState, ClientSessionState newState) {
                System.out.println("🔄 [" + currentTime() + "] Estado: " + oldState + " → " + newState);
            }
            
            @Override
            public void onError(String context, Throwable error) {
                System.out.println("⚠️  [" + currentTime() + "] Error: " + context);
            }
            
            @Override
            public void onReconnecting(int attemptNumber) {
                System.out.println("🔁 [" + currentTime() + "] Reconectando... intento " + attemptNumber);
            }
            
            @Override
            public void onDisconnected(String reason) {
                System.out.println("📴 [" + currentTime() + "] Desconectado: " + reason);
            }
        });
        
        // Conectar
        System.out.println("Conectando...\n");
        client.connect().get();
        System.out.println("\n✓ Conectado - Ahora DETÉN el servidor para ver la reconexión\n");
        
        // Esperar largo tiempo para que puedas detener/reiniciar el servidor
        final long sleepInterval = 1000;
        for (int i = 1; i <= 60; i++) {  // 60 segundos = 1 minuto
            if (i % 10 == 0) System.out.println("[" + i + "s] Estado: " + client.getState() + " | Reconectando: " + client.isReconnecting());
            Thread.sleep(sleepInterval);
        }
        
        // Cleanup
        System.out.println("\nFinalizando test...");
        client.shutdown();
        
        System.out.println("\n✓ Test de reconexión completado");
    }
    
    private static String currentTime() {
        return java.time.LocalTime.now().format(
            java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss")
        );
    }
}
