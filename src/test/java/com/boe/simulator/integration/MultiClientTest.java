package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.LoginRequestMessage;

public class MultiClientTest {
    public static void main(String[] args) throws Exception {
        // Cliente 1
        Thread t1 = new Thread(() -> {
            try {
                BoeConnectionHandler c1 = new BoeConnectionHandler("localhost", 8080);
                c1.connect().get();
                LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "S001");
                c1.sendMessage(login.toBytes()).get();
                Thread.sleep(2000);
                c1.disconnect().get();
            } catch (Exception e) { e.printStackTrace(); }
        });

        // Cliente 2
        Thread t2 = new Thread(() -> {
            try {
                BoeConnectionHandler c2 = new BoeConnectionHandler("localhost", 8080);
                c2.connect().get();
                LoginRequestMessage login = new LoginRequestMessage("TEST", "TEST", "S002");
                c2.sendMessageRaw(login.toBytes()).get();
                Thread.sleep(2000);
                c2.disconnect().get();
            } catch (Exception e) { e.printStackTrace(); }
        });

        t1.start();
        Thread.sleep(100); // Pequeño offset
        t2.start();

        t1.join();
        t2.join();

        System.out.println("✓ Both clients completed");
    }
}
