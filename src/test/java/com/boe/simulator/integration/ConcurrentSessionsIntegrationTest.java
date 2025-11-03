package com.boe.simulator.integration;


import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.*;

import java.util.concurrent.CompletableFuture;

public class MultiUserSessionTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== PHASE 5 Test: Session Management ===\n");

        // Client 1: USER
        System.out.println("Starting Client 1 (USER)...");
        BoeConnectionHandler client1 = createClient("Client1");
        client1.connect().get();
        LoginRequestMessage login1 = new LoginRequestMessage("USER", "PASS", "S001");
        client1.sendMessageRaw(login1.toBytes()).get();
        CompletableFuture<Void> listener1 = client1.startListener();
        Thread.sleep(1000);
        System.out.println("✓ Client 1 connected and authenticated\n");

        // Client 2: TEST
        System.out.println("Starting Client 2 (TEST)...");
        BoeConnectionHandler client2 = createClient("Client2");
        client2.connect().get();
        LoginRequestMessage login2 = new LoginRequestMessage("TEST", "TEST", "S002");
        client2.sendMessageRaw(login2.toBytes()).get();
        CompletableFuture<Void> listener2 = client2.startListener();
        Thread.sleep(1000);
        System.out.println("✓ Client 2 connected and authenticated\n");

        // Client 3: ADMIN
        System.out.println("Starting Client 3 (ADMIN)...");
        BoeConnectionHandler client3 = createClient("Client3");
        client3.connect().get();
        LoginRequestMessage login3 = new LoginRequestMessage("ADMN", "ADMI", "S003");
        client3.sendMessageRaw(login3.toBytes()).get();
        CompletableFuture<Void> listener3 = client3.startListener();
        Thread.sleep(1000);
        System.out.println("✓ Client 3 connected and authenticated\n");

        // Wait and observe (server should show 3 active sessions)
        System.out.println("Waiting 10 seconds...");
        System.out.println("Check server logs for session summary!\n");
        Thread.sleep(10000);

        // Disconnect Client 2
        System.out.println("Disconnecting Client 2...");
        client2.stopListener();
        client2.disconnect().get();
        Thread.sleep(2000);
        System.out.println("✓ Client 2 disconnected\n");

        // Wait more (server should show 2 active sessions now)
        System.out.println("Waiting 5 more seconds...");
        Thread.sleep(5000);

        // Cleanup remaining clients
        System.out.println("\nCleaning up...");
        client1.stopListener();
        client1.disconnect().get();
        client3.stopListener();
        client3.disconnect().get();

        System.out.println("\n✓ Test completed!");
        System.out.println("Check server logs for final statistics.");
    }

    private static BoeConnectionHandler createClient(String name) {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println(name + " - Login: " + (char)response.getLoginResponseStatus() +
                        " - " + response.getLoginResponseText());
            }

            @Override
            public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
                System.out.println(name + " - Heartbeat received (seq=" + heartbeat.getSequenceNumber() + ")");
            }
        });
        return client;
    }
}