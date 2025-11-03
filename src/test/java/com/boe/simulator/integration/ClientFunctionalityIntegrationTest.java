package com.boe.simulator.integration;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.config.BoeClientConfiguration;
import com.boe.simulator.client.session.ClientSessionState;
import com.boe.simulator.client.session.SessionEventListener;
import com.boe.simulator.protocol.message.LoginResponseMessage;

public class ClientFunctionalityIntegrationTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║       Test BoeClient - Paso 2 Completo                 ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        testSimpleUsage();
        
        Thread.sleep(2000);

        testAdvancedUsage();
        
        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║       Test Completado                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    private static void testSimpleUsage() throws Exception {
        System.out.println("═══ Test 1: Uso Simple ═══\n");
        
        // Crear cliente de forma simple
        BoeClient client = BoeClient.create("localhost", 8080, "USER", "PASS");
        
        // Conectar y login automático
        client.connect().get();
        System.out.println("✓ Cliente conectado y autenticado");
        System.out.println("  Estado: " + client.getState());
        System.out.println("  ¿Conectado? " + client.isConnected());
        System.out.println("  ¿Autenticado? " + client.isAuthenticated());
        
        // Esperar un poco
        Thread.sleep(3000);
        
        // Desconectar
        client.disconnect().get();
        System.out.println("✓ Cliente desconectado");
        System.out.println("  Estado final: " + client.getState());
        
        System.out.println("\n✓ Test 1 completado\n");
    }

    private static void testAdvancedUsage() throws Exception {
        System.out.println("═══ Test 2: Uso Avanzado con Callbacks ═══\n");
        
        // Configuración personalizada
        BoeClientConfiguration config = BoeClientConfiguration.builder()
            .host("localhost")
            .port(8080)
            .credentials("TEST", "TEST")
            .sessionSubID("TST1")
            .heartbeatInterval(10)
            .autoHeartbeat(true)
            .autoReconnect(false)
            .build();
        
        BoeClient client = new BoeClient(config);
        
        // Setup listeners para eventos
        client.setSessionListener(new SessionEventListener() {
            @Override
            public void onConnected(String host, int port) {
                System.out.println("📡 Evento: Conectado a " + host + ":" + port);
            }
            
            @Override
            public void onLoginSuccess(LoginResponseMessage response) {
                System.out.println("✅ Evento: Login exitoso - " + response.getLoginResponseText());
            }
            
            @Override
            public void onLoginFailed(LoginResponseMessage response) {
                System.out.println("❌ Evento: Login fallido - " + response.getLoginResponseText());
            }
            
            @Override
            public void onStateChanged(ClientSessionState oldState, ClientSessionState newState) {
                System.out.println("🔄 Evento: Estado cambió de " + oldState + " → " + newState);
            }
            
            @Override
            public void onDisconnected(String reason) {
                System.out.println("📴 Evento: Desconectado - " + reason);
            }
            
            @Override
            public void onError(String context, Throwable error) {
                System.out.println("⚠️  Evento: Error en " + context + " - " + error.getMessage());
            }
        });
        
        // Conectar
        System.out.println("Iniciando conexión...\n");
        client.connect().get();
        
        System.out.println("\nEstado actual: " + client.getState());
        System.out.println("Configuración: " + client.getConfiguration());
        
        // Esperar un poco para ver heartbeats (si están habilitados)
        Thread.sleep(5000);
        
        // Desconectar
        System.out.println("\nIniciando desconexión...\n");
        client.disconnect().get();
        
        System.out.println("\n✓ Test 2 completado");
    }
}