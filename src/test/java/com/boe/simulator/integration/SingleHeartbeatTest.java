package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.ClientHeartbeatMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;

public class SingleHeartbeatTest {
    public static void main(String[] args) throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        client.connect().get();

        // Send login
        LoginRequestMessage loginRequest = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessage(loginRequest.toBytes()).get();

        Thread.sleep(500);

        // Send heartbeat
        ClientHeartbeatMessage heartbeat = new ClientHeartbeatMessage((byte) 0, (short) 1);
        client.sendMessageRaw(heartbeat.toBytes()).get();

        System.out.println("✓ Heartbeat sent");

        Thread.sleep(1000);
        client.disconnect().get();
    }
}