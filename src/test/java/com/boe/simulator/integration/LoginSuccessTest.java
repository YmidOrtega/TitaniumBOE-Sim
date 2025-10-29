package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;

public class LoginSuccessTest {
    public static void main(String[] args) throws Exception {
        System.out.println("=== FASE 3 Test: Login Success ===\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        // Setup listener
        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("\n>>> LoginResponse Received <<<");
                System.out.println("Status: " + (char)response.getLoginResponseStatus());
                System.out.println("Message: " + response.getLoginResponseText());
                System.out.println("LastSeq: " + response.getLastReceivedSequenceNumber());

                if (response.isAccepted()) {
                    System.out.println("✓✓ LOGIN ACCEPTED!");
                } else if (response.isRejected()) {
                    System.out.println("✗✗ LOGIN REJECTED!");
                } else {
                    System.out.println("⚠⚠ SESSION IN USE!");
                }
            }
        });

        // Connect
        System.out.println("Connecting...");
        client.connect().get();
        System.out.println("✓ Connected\n");

        // Start listener
        System.out.println("Starting listener...");
        client.startListener();
        Thread.sleep(1000);  // Wait for listener to be fully ready
        System.out.println("✓ Listener ready\n");

        // Send login
        System.out.println("Sending LoginRequest...");
        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessageRaw(login.toBytes()).get();
        System.out.println("✓ LoginRequest sent\n");

        // Wait for response
        System.out.println("Waiting for response...");
        Thread.sleep(3000);

        // Cleanup
        System.out.println("\nCleaning up...");
        client.stopListener();
        client.disconnect().get();
        System.out.println("✓ Test completed");
    }
}
