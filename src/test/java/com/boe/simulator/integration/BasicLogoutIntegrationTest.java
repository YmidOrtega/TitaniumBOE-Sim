package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;

public class BasicLogoutIntegrationTest {
    public static void main(String[] args) throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login Response: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                System.out.println("\n✓ Received LogoutResponse:");
                System.out.println("  Reason: " + (char)response.getLogoutReason());
                System.out.println("  Message: " + response.getLogoutReasonText());
                System.out.println("  ✓✓ LOGOUT SUCCESSFUL!");
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        // Login
        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "S001");
        client.sendMessageRaw(login.toBytes()).get();
        System.out.println("✓ Login sent");

        Thread.sleep(1000);

        // Logout
        LogoutRequestMessage logout = new LogoutRequestMessage();
        client.sendMessageRaw(logout.toBytes()).get();
        System.out.println("✓ Logout sent");

        Thread.sleep(1000);

        client.stopListener();
        client.disconnect().get();
    }
}
