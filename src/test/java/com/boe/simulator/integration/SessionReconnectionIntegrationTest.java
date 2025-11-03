package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;

public class SessionReconnectionIntegrationTest {
    public static void main(String[] args) throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login Response: " + (char)response.getLoginResponseStatus() + " - " + response.getLoginResponseText());
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                System.out.println("✓ Logout Response received");
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        // First login
        LoginRequestMessage login1 = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessageRaw(login1.toBytes()).get();
        System.out.println("✓ First login sent");
        Thread.sleep(1000);

        // Logout
        LogoutRequestMessage logout = new LogoutRequestMessage();
        client.sendMessageRaw(logout.toBytes()).get();
        System.out.println("✓ Logout sent");
        Thread.sleep(1000);

        client.stopListener();
        client.disconnect().get();

        // New connection - Second login (should work)
        System.out.println("\n--- Reconnecting ---");
        BoeConnectionHandler client2 = new BoeConnectionHandler("localhost", 8080);
        client2.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Second Login Response: " + (char)response.getLoginResponseStatus());
                if (response.isAccepted()) {
                    System.out.println("  ✓✓ RE-LOGIN SUCCESSFUL!");
                }
            }
        });

        client2.connect().get();
        client2.startListener();
        Thread.sleep(300);

        LoginRequestMessage login2 = new LoginRequestMessage("RLOG", "PASS", "S002");
        client2.sendMessageRaw(login2.toBytes()).get();
        System.out.println("✓ Second login sent (same user, new session)");

        Thread.sleep(1000);

        client2.stopListener();
        client2.disconnect().get();
    }
}
