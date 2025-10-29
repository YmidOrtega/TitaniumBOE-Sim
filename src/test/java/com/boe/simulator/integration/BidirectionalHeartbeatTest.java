package com.boe.simulator.integration;

import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.ServerHeartbeatMessage;
import com.boe.simulator.client.connection.BoeConnectionHandler;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

public class BidirectionalHeartbeatTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== PHASE 4 Test: Bidirectional Heartbeat ===\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        AtomicInteger heartbeatsReceived = new AtomicInteger(0);

        // Setup listener
        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus() +
                        " - " + response.getLoginResponseText());
            }

            @Override
            public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
                int count = heartbeatsReceived.incrementAndGet();
                System.out.println("✓ ServerHeartbeat #" + count + " received (seq=" +
                        heartbeat.getSequenceNumber() + ")");
            }
        });

        // Connect
        client.connect().get();
        System.out.println("✓ Connected\n");

        // Login
        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessageRaw(login.toBytes()).get();
        System.out.println("✓ Login sent\n");

        // Start listener
        CompletableFuture<Void> listenerFuture = client.startListener();
        Thread.sleep(500);

        // Wait and observe heartbeats (server will send every 10 seconds)
        System.out.println("Waiting 35 seconds to observe heartbeats...\n");

        for (int i = 1; i <= 7; i++) {
            Thread.sleep(5000);
            System.out.println("[" + (i * 5) + "s] Heartbeats received so far: " + heartbeatsReceived.get());
        }

        // Cleanup
        System.out.println("\n✓ Test completed - Total heartbeats: " + heartbeatsReceived.get());
        client.stopListener();
        client.disconnect().get();
    }
}