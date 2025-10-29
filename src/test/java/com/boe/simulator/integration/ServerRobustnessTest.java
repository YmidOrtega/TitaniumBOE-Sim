package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.*;

import java.util.concurrent.CompletableFuture;

public class ServerRobustnessTest {
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    PHASE 6: Final Server Robustness Test              ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        // Test 1: Normal operation
        System.out.println("═══ Test 1: Normal Operations ═══");
        testNormalOperation();
        Thread.sleep(2000);

        // Test 2: Multiple rapid connections
        System.out.println("\n═══ Test 2: Rapid Connections ═══");
        testRapidConnections();
        Thread.sleep(2000);

        // Test 3: Long-running session
        System.out.println("\n═══ Test 3: Long-Running Session ═══");
        testLongRunningSession();

        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║    All Tests Completed - Check Server Logs            ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    private static void testNormalOperation() throws Exception {
        System.out.println("Testing normal login/logout flow...");

        BoeConnectionHandler client = createClient("Normal");
        client.connect().get();

        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "TST1");
        client.sendMessageRaw(login.toBytes()).get();

        CompletableFuture<Void> listener = client.startListener();
        Thread.sleep(2000);

        // Send logout
        LogoutRequestMessage logout = new LogoutRequestMessage();
        client.sendMessageRaw(logout.toBytes()).get();

        Thread.sleep(1000);

        client.stopListener();
        client.disconnect().get();

        System.out.println("✓ Normal operation test completed");
    }

    private static void testRapidConnections() throws Exception {
        System.out.println("Creating 5 rapid connections...");

        BoeConnectionHandler[] clients = new BoeConnectionHandler[5];
        CompletableFuture<Void>[] listeners = new CompletableFuture[5];

        // Create all clients rapidly
        for (int i = 0; i < 5; i++) {
            clients[i] = createClient("Client" + (i + 1));
            clients[i].connect().get();

            LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "RP" + i);

            // Only first one will succeed, others will fail (duplicate session)
            clients[i].sendMessageRaw(login.toBytes()).get();
            listeners[i] = clients[i].startListener();

            Thread.sleep(200);  // Small delay between connections
        }

        System.out.println("✓ All connections created");
        Thread.sleep(3000);

        // Cleanup
        System.out.println("Disconnecting all clients...");
        for (int i = 0; i < 5; i++) {
            try {
                clients[i].stopListener();
                clients[i].disconnect().get();
            } catch (Exception e) {
                // Some may already be disconnected
            }
        }

        System.out.println("✓ Rapid connections test completed");
    }

    private static void testLongRunningSession() throws Exception {
        System.out.println("Starting long-running session (30 seconds)...");

        BoeConnectionHandler client = createClient("LongRunning");
        client.connect().get();

        LoginRequestMessage login = new LoginRequestMessage("TEST", "TEST", "LNG1");
        client.sendMessageRaw(login.toBytes()).get();

        CompletableFuture<Void> listener = client.startListener();

        System.out.println("Session started - observing heartbeats for 30 seconds...");

        for (int i = 1; i <= 6; i++) {
            Thread.sleep(5000);
            System.out.println("  [" + (i * 5) + "s] Session still active...");
        }

        // Graceful logout
        System.out.println("Sending logout...");
        LogoutRequestMessage logout = new LogoutRequestMessage();
        client.sendMessageRaw(logout.toBytes()).get();

        Thread.sleep(1000);

        client.stopListener();
        client.disconnect().get();

        System.out.println("✓ Long-running session test completed");
    }

    /**
     * Create a client with logging
     */
    private static BoeConnectionHandler createClient(String name) {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                char status = (char) response.getLoginResponseStatus();
                System.out.println("  " + name + " - Login: " + status + " - " + response.getLoginResponseText());
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                System.out.println("  " + name + " - Logout: " + response.getLogoutReasonText());
            }

            @Override
            public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
                // Don't log every heartbeat to avoid spam
            }
        });
        return client;
    }
}