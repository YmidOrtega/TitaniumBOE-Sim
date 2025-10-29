package com.boe.simulator.client;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.client.config.BoeClientConfiguration;
import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.heartbeat.ClientHeartbeatManager;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.client.session.ClientSessionState;
import com.boe.simulator.client.session.SessionEventListener;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.ServerHeartbeatMessage;

public class BoeClient {
    private static final Logger LOGGER = Logger.getLogger(BoeClient.class.getName());

    private final BoeClientConfiguration config;
    private final BoeConnectionHandler connectionHandler;
    private final AtomicReference<ClientSessionState> state;
    private final ClientHeartbeatManager heartbeatManager;

    private SessionEventListener sessionListener;
    private CompletableFuture<Void> listenerFuture;

    public BoeClient(BoeClientConfiguration config) {
        this.config = config;
        this.connectionHandler = new BoeConnectionHandler(config.getHost(), config.getPort());
        this.state = new AtomicReference<>(ClientSessionState.DISCONNECTED);
        this.heartbeatManager = new ClientHeartbeatManager(connectionHandler, config.getHeartbeatIntervalSeconds(), 30);

        LOGGER.setLevel(config.getLogLevel());
        LOGGER.log(Level.INFO, "BoeClient initialized: {0}", config);

        setupMessageListener();
        setupHeartbeatTimeout();
    }

    private void setupMessageListener() {
        connectionHandler.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                handleLoginResponse(response);
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                handleLogoutResponse(response);
            }

            @Override
            public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
                heartbeatManager.notifyServerHeartbeatReceived();
                LOGGER.log(Level.FINE, "Server heartbeat received: seq={0}", heartbeat.getSequenceNumber());
            }
        });
    }

    private void setupHeartbeatTimeout() {
        heartbeatManager.setTimeoutListener(() -> {
            LOGGER.severe("Server heartbeat timeout detected - connection lost");
            setState(ClientSessionState.ERROR);

            if (sessionListener != null) sessionListener.onError("heartbeat_timeout", new Exception("Server heartbeat timeout"));
        });
    }

    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            try {
                setState(ClientSessionState.CONNECTING);

                // TCP connect
                connectionHandler.connect().get();
                setState(ClientSessionState.CONNECTED);

                if (sessionListener != null) sessionListener.onConnected(config.getHost(), config.getPort());

                // Send login
                setState(ClientSessionState.AUTHENTICATING);
                sendLogin();

                // Start listener
                listenerFuture = connectionHandler.startListener();

                // Wait a bit for login response
                Thread.sleep(1000);

            } catch (Exception e) {
                setState(ClientSessionState.ERROR);
                LOGGER.log(Level.SEVERE, "Connection failed", e);
                if (sessionListener != null) sessionListener.onError("connect", e);

                throw new RuntimeException("Connection failed", e);
            }
        });
    }

    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            try {
                setState(ClientSessionState.DISCONNECTING);

                if (heartbeatManager.isActive()) heartbeatManager.stop();

                if (state.get().isAuthenticated()) {
                    sendLogout();
                    Thread.sleep(500);
                }

                if (listenerFuture != null) connectionHandler.stopListener();

                connectionHandler.disconnect().get();
                setState(ClientSessionState.DISCONNECTED);

                if (sessionListener != null) sessionListener.onDisconnected("User requested");

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Disconnect error", e);
                setState(ClientSessionState.ERROR);
            }
        });
    }

    private void sendLogin() throws Exception {
        LoginRequestMessage login = new LoginRequestMessage(
                config.getUsername(),
                config.getPassword(),
                config.getSessionSubID()
        );

        connectionHandler.sendMessageRaw(login.toBytes()).get();
        LOGGER.log(Level.INFO, "Login request sent for user: {0}", config.getUsername());
    }

    private void sendLogout() throws Exception {
        LogoutRequestMessage logout = new LogoutRequestMessage();
        connectionHandler.sendMessageRaw(logout.toBytes()).get();
        LOGGER.info("Logout request sent");
    }

    private void handleLoginResponse(LoginResponseMessage response) {
        if (response.isAccepted()) {
            setState(ClientSessionState.AUTHENTICATED);
            LOGGER.log(Level.INFO, "Login successful: {0}", response.getLoginResponseText());

            if (sessionListener != null) sessionListener.onLoginSuccess(response);

            if (config.isAutoHeartbeat()) {
                heartbeatManager.start();
                LOGGER.info("Auto-heartbeat enabled");
            }

        } else {
            setState(ClientSessionState.ERROR);
            LOGGER.log(Level.WARNING, "Login failed: {0}", response.getLoginResponseText());

            if (sessionListener != null) sessionListener.onLoginFailed(response);

        }
    }

    private void handleLogoutResponse(LogoutResponseMessage response) {
        LOGGER.log(Level.INFO, "Logout completed: {0}", response.getLogoutReasonText());

        if (sessionListener != null) sessionListener.onLogoutCompleted(response);

    }

    public void shutdown() {
        try {
            if (isConnected()) disconnect().get();

            heartbeatManager.shutdown();
        }catch (InterruptedException | ExecutionException | IllegalStateException e) {
            LOGGER.log(Level.WARNING, "Shutdown error", e);
            if (e instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    public void setSessionListener(SessionEventListener listener) {
        this.sessionListener = listener;
    }

    public ClientSessionState getState() {
        return state.get();
    }

    public boolean isConnected() {
        return state.get().isConnected();
    }

    public boolean isAuthenticated() {
        return state.get().isAuthenticated();
    }

    public BoeClientConfiguration getConfiguration() {
        return config;
    }

    public BoeConnectionHandler getConnectionHandler() {
        return connectionHandler;
    }

    public ClientHeartbeatManager getHeartbeatManager() {
        return heartbeatManager;
    }

    private void setState(ClientSessionState newState) {
        ClientSessionState oldState = state.getAndSet(newState);

        if (oldState != newState) {
            LOGGER.log(Level.INFO, "State changed: {0} -> {1}", new Object[]{oldState, newState});

            if (sessionListener != null) sessionListener.onStateChanged(oldState, newState);
        }
    }

    public static BoeClient create(String host, int port, String username, String password) {
        BoeClientConfiguration config = BoeClientConfiguration.builder()
                .host(host)
                .port(port)
                .credentials(username, password)
                .build();

        return new BoeClient(config);
    }
}
