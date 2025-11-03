package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.ClientHeartbeatMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;

public class SequenceTest {
    public static void main(String[] args) throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        client.connect().get();

        // 1. Login
        LoginRequestMessage loginRequest = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessage(loginRequest.toBytes()).get();
        System.out.println("✓ Sent: LoginRequest");
        Thread.sleep(500);

        // 2. Heartbeat 1
        ClientHeartbeatMessage hb1 = new ClientHeartbeatMessage((byte) 0, 1);
        client.sendMessageRaw(hb1.toBytes()).get();
        System.out.println("✓ Sent: Heartbeat #1");
        Thread.sleep(500);

        // 3. Heartbeat 2
        ClientHeartbeatMessage hb2 = new ClientHeartbeatMessage((byte) 0, 2);
        client.sendMessageRaw(hb2.toBytes()).get();
        System.out.println("✓ Sent: Heartbeat #2");
        Thread.sleep(500);

        // 4. Logout
        LogoutRequestMessage logoutRequest = new LogoutRequestMessage();
        client.sendMessageRaw(logoutRequest.toBytes()).get();
        System.out.println("✓ Sent: LogoutRequest");

        Thread.sleep(1000);
        client.disconnect().get();
    }
}
