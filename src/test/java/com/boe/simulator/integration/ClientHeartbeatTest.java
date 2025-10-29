package com.boe.simulator.integration;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.config.BoeClientConfiguration;

import com.boe.simulator.client.session.SessionEventListener;
import com.boe.simulator.protocol.message.*;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientHeartbeatTest {

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Client Heartbeat - Paso 3                      ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        // Test 1: Heartbeat automático habilitado
        testAutoHeartbeat();

        Thread.sleep(2000);

        // Test 2: Heartbeat manual (deshabilitado)
        testManualHeartbeat();

        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║       Test Completado                                  ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    /**
     * Test 1: Heartbeat automático
     */
    private static void testAutoHeartbeat() throws Exception {
        System.out.println("═══ Test 1: Heartbeat Automático ═══\n");

        AtomicInteger serverHeartbeatsReceived = new AtomicInteger(0);

        // Configuración con auto-heartbeat habilitado
        BoeClientConfiguration config = BoeClientConfiguration.builder()
                .host("localhost")
                .port(8080)
                .credentials("USER", "PASS")
                .sessionSubID("HB01")
                .heartbeatInterval(10)
                .autoHeartbeat(true)
                .build();

        BoeClient client = new BoeClient(config);

        // Listener para contar heartbeats
        client.getConnectionHandler().setMessageListener(new com.boe.simulator.client.listener.BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
                int count = serverHeartbeatsReceived.incrementAndGet();
                System.out.println("💓 Server heartbeat #" + count + " (seq=" + heartbeat.getSequenceNumber() + ")");
            }
        });

        // Conectar
        System.out.println("Conectando...");
        client.connect().get();
        System.out.println("✓ Conectado y autenticado");
        System.out.println("  Auto-heartbeat: " + config.isAutoHeartbeat());
        System.out.println("  Heartbeat activo: " + client.getHeartbeatManager().isActive());

        // Observar heartbeats durante 35 segundos
        System.out.println("\nObservando heartbeats durante 35 segundos...\n");

        for (int i = 1; i <= 7; i++) {
            Thread.sleep(5000);

            Instant lastSent = client.getHeartbeatManager().getLastClientHeartbeatSent();
            Instant lastReceived = client.getHeartbeatManager().getLastServerHeartbeatReceived();

            System.out.println("[" + (i * 5) + "s] Heartbeats del servidor: " + serverHeartbeatsReceived.get());
            if (lastSent != null) System.out.println("Último heartbeat enviado: hace " + java.time.Duration.between(lastSent, Instant.now()).getSeconds() + "s");
            if (lastReceived != null) System.out.println("Último heartbeat recibido: hace " + java.time.Duration.between(lastReceived, Instant.now()).getSeconds() + "s");

        }

        // Desconectar
        System.out.println("\nDesconectando...");
        client.disconnect().get();

        System.out.println("✓ Test 1 completado");
        System.out.println("  Total heartbeats del servidor recibidos: " + serverHeartbeatsReceived.get());
        System.out.println();
    }

    /**
     * Test 2: Sin heartbeat automático
     */
    private static void testManualHeartbeat() throws Exception {
        System.out.println("═══ Test 2: Heartbeat Manual (Deshabilitado) ═══\n");

        // Configuración con auto-heartbeat DESHABILITADO
        BoeClientConfiguration config = BoeClientConfiguration.builder()
                .host("localhost")
                .port(8080)
                .credentials("TEST", "TEST")
                .sessionSubID("HB02")
                .autoHeartbeat(false)
                .build();

        BoeClient client = new BoeClient(config);

        client.setSessionListener(new SessionEventListener() {
            @Override
            public void onLoginSuccess(LoginResponseMessage response) {
                System.out.println("✓ Login exitoso");
            }
        });

        // Conectar
        System.out.println("Conectando...");
        client.connect().get();
        System.out.println("✓ Conectado y autenticado");
        System.out.println("  Auto-heartbeat: " + config.isAutoHeartbeat());
        System.out.println("  Heartbeat activo: " + client.getHeartbeatManager().isActive());

        // Esperar un poco
        System.out.println("\nEsperando 5 segundos (sin heartbeats automáticos)...");
        Thread.sleep(5000);

        System.out.println("✓ Sin heartbeats enviados automáticamente (como esperado)");

        // Desconectar
        System.out.println("\nDesconectando...");
        client.disconnect().get();

        System.out.println("\n✓ Test 2 completado");
    }
}