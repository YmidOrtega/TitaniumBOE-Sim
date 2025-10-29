package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;

public class DuplicateSessionTest {
    public static void main(String[] args) throws Exception {
        // Cliente 1 - Login exitoso
        BoeConnectionHandler client1 = new BoeConnectionHandler("localhost", 8080);
        client1.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("Client1: Status=" + (char)response.getLoginResponseStatus());
            }
        });
        client1.connect().get();
        client1.startListener();
        Thread.sleep(300);

        LoginRequestMessage login1 = new LoginRequestMessage("USER", "PASS", "S001");
        client1.sendMessageRaw(login1.toBytes()).get();
        System.out.println("✓ Client 1 login sent");

        Thread.sleep(1000);

        // Cliente 2 - Intenta login con mismo usuario
        BoeConnectionHandler client2 = new BoeConnectionHandler("localhost", 8080);
        client2.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("\nClient2: Status=" + (char)response.getLoginResponseStatus());
                System.out.println("Client2: Message=" + response.getLoginResponseText());

                if (response.getLoginResponseStatus() == 'S') System.out.println("✓✓ DUPLICATE SESSION CORRECTLY DETECTED!");

            }
        });
        client2.connect().get();
        client2.startListener();
        Thread.sleep(300);

        LoginRequestMessage login2 = new LoginRequestMessage("USER", "PASS", "S002");
        client2.sendMessageRaw(login2.toBytes()).get();
        System.out.println("✓ Client 2 login sent (duplicate user)");

        Thread.sleep(2000);

        // Cleanup
        client1.stopListener();
        client2.stopListener();
        client1.disconnect().get();
        client2.disconnect().get();
    }
}
