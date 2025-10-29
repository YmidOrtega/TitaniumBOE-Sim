package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;

public class LoginRejectedTest {
    public static void main(String[] args) throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("\n✓ Received LoginResponse:");
                System.out.println("  Status: " + (char)response.getLoginResponseStatus());
                System.out.println("  Message: " + response.getLoginResponseText());

                if (response.isRejected()) System.out.println("  ✓✓ LOGIN CORRECTLY REJECTED!");
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(500);

        // Send login with INVALID credentials
        LoginRequestMessage login = new LoginRequestMessage("USER", "WRONGPASS", "S001");
        client.sendMessageRaw(login.toBytes()).get();

        System.out.println("✓ Login request sent (wrong password)");
        Thread.sleep(2000);

        client.stopListener();
        client.disconnect().get();
    }
}
