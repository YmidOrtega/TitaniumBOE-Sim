package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.LoginRequestMessage;

public class BasicLoginIntegrationTest {
    public static void main(String[] args) throws Exception {
        // Connect to the server
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        client.connect().get();

        System.out.println("✓ Connected to server");

        // Send login request
        LoginRequestMessage loginRequest = new LoginRequestMessage(
                "USER",      // username
                "PASS",      // password
                "S001",      // sessionSubID
                (byte) 0     // matchingUnit
        );

        byte[] messageBytes = loginRequest.toBytes();
        client.sendMessageRaw(messageBytes).get();

        System.out.println("✓ Login request sent");

        // Wait to see server logs
        Thread.sleep(2000);

        // Disconnect
        client.disconnect().get();
        System.out.println("✓ Disconnected");
    }
}