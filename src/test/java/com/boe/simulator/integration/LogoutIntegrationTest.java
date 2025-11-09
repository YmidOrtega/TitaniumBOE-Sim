package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.server.CboeServer;
import com.boe.simulator.server.config.ServerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.CountDownLatch;

public class LogoutIntegrationTest {

    private CboeServer server;
    private CountDownLatch listenerReadyLatch;

    @BeforeEach
    void setUp() throws Exception {
        // Enable DEMO_MODE for tests
        System.setProperty("DEMO_MODE", "true");
        
        ServerConfiguration config = ServerConfiguration.builder()
                .port(8080)
                .logLevel(Level.INFO)
                .build();
        server = new CboeServer(config);
        server.start();
        
        // Give server time to initialize and create demo users
        Thread.sleep(2000);
        
        listenerReadyLatch = new CountDownLatch(1);
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.shutdown();
        }
        // Clean up system property
        System.clearProperty("DEMO_MODE");
    }

    @Test
    void testSuccessfulLogout() throws Exception {
        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        CompletableFuture<LoginResponseMessage> loginFuture = new CompletableFuture<>();
        CompletableFuture<LogoutResponseMessage> logoutFuture = new CompletableFuture<>();

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("Client: Received LoginResponse");
                loginFuture.complete(response);
                listenerReadyLatch.countDown(); // Signal that listener is ready after login response
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                System.out.println("Client: Received LogoutResponse");
                logoutFuture.complete(response);
            }

            @Override
            public void onServerHeartbeat(com.boe.simulator.protocol.message.ServerHeartbeatMessage heartbeat) {
                // Consume heartbeats to prevent unexpected stream closure
            }
        });

        client.connect().get(10, TimeUnit.SECONDS);
        client.startListener();
        listenerReadyLatch.await(10, TimeUnit.SECONDS); // Wait for listener to process initial messages

        // Login
        LoginRequestMessage login = new LoginRequestMessage("TRD1", "Pass1234!", "S001");
        client.sendMessageRaw(login.toBytes()).get(10, TimeUnit.SECONDS);

        LoginResponseMessage loginResponse = loginFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(loginResponse);
        assertEquals('A', loginResponse.getLoginResponseStatus());

        // Logout
        LogoutRequestMessage logout = new LogoutRequestMessage();
        client.sendMessageRaw(logout.toBytes()).get(10, TimeUnit.SECONDS);

        LogoutResponseMessage logoutResponse = logoutFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(logoutResponse);
        assertEquals('U', logoutResponse.getLogoutReason());

        client.stopListener();
        client.disconnect().get(10, TimeUnit.SECONDS);
    }
}
