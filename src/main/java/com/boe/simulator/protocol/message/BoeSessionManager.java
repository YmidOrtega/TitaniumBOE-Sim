package com.boe.simulator.protocol.message;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.logging.Level;

import com.boe.simulator.connection.BoeConnectionHandler;
import com.boe.simulator.connection.BoeMessageListener;

public class BoeSessionManager {
    private static final Logger LOGGER = Logger.getLogger(BoeSessionManager.class.getName());
    private static final long HEARTBEAT_INTERVAL_SECONDS = 10;
    
    private final BoeConnectionHandler connectionHandler;
    private final AtomicInteger sessionIdCounter = new AtomicInteger(1);
    private final AtomicInteger sequenceNumber = new AtomicInteger(1);
    private final ScheduledExecutorService heartbeatScheduler = Executors.newSingleThreadScheduledExecutor();
    
    private Instant lastHeartbeatTime;
    private volatile SessionState sessionState;
    private String sessionSubID;
    private String username;
    private String password;

    public BoeSessionManager(BoeConnectionHandler handler) {
        this.connectionHandler = handler;
        this.sessionState = SessionState.DISCONNECTED;
        this.connectionHandler.setMessageListener(new BoeMessageListener() {
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
                handleServerHeartbeat(heartbeat);
            }
        });
    }

    public CompletableFuture<Void> login(String username, String password) {
        this.username = username;
        this.password = password;
        
        return CompletableFuture.runAsync(() -> {
            try {
                setSessionState(SessionState.CONNECTING);
                connectionHandler.connect().get();
                
                setSessionState(SessionState.AUTHENTICATING);
                sendLoginRequest();

                Thread.sleep(100);
                
                setSessionState(SessionState.AUTHENTICATED);
                startHeartbeat();
                
                LOGGER.info("Login successful for user: " + username);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                setSessionState(SessionState.ERROR);
                LOGGER.log(Level.SEVERE, "Login interrupted", e);
                throw new RuntimeException("Login interrupted", e);
            } catch (Exception e) {
                setSessionState(SessionState.ERROR);
                LOGGER.log(Level.SEVERE, "Login failed", e);
                throw new RuntimeException("Login failed", e);
            }
        });
    }

    public CompletableFuture<Void> logout() {
        return CompletableFuture.runAsync(() -> {
            try {
                setSessionState(SessionState.DISCONNECTING);
                stopHeartbeat();
                sendLogoutRequest();
                connectionHandler.disconnect().get();
                setSessionState(SessionState.DISCONNECTED);
                LOGGER.info("Logout successful for user: " + username);
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Logout failed", e);
                throw new RuntimeException("Logout failed", e);
            }
        });
    }

    private void sendLoginRequest() {
        try {
            LOGGER.info("Sending login request for user: " + username);
            
            String sessionSubID = generateSessionSubID();
            LoginRequestMessage loginMessage = new LoginRequestMessage(
                username, 
                password, 
                sessionSubID, 
                (byte) 0
            );
            
            byte[] messageBytes = loginMessage.toBytes();
            connectionHandler.sendMessage(messageBytes).get();
            
            LOGGER.info("Login request sent successfully: " + loginMessage);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send login request", e);
            throw new RuntimeException("Failed to send login request", e);
        }
    }

    private void sendLogoutRequest() {
        try {
            LOGGER.info("Sending logout request");
            
            LogoutRequestMessage logoutRequestMessage = new LogoutRequestMessage();
            byte[] messageBytes = logoutRequestMessage.toBytes();
            connectionHandler.sendMessage(messageBytes).get();
            
            LOGGER.info("Logout request sent successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to send logout request", e);
            throw new RuntimeException("Failed to send logout request", e);
        }
    }

    private void startHeartbeat() {
        heartbeatScheduler.scheduleAtFixedRate(() -> {
            try {
                sendHeartbeat();
                lastHeartbeatTime = Instant.now();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Heartbeat failed", e);
                setSessionState(SessionState.HEARTBEAT_TIMEOUT);
            }
        }, HEARTBEAT_INTERVAL_SECONDS, HEARTBEAT_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    private void stopHeartbeat() {
        heartbeatScheduler.shutdown();
    }

    private void sendHeartbeat() {
        try {
            LOGGER.fine("Sending heartbeat");
            
            ClientHeartbeatMessage heartbeatMessage = new ClientHeartbeatMessage();
            heartbeatMessage.setSequenceNumber(sequenceNumber.getAndIncrement());
            
            byte[] messageBytes = heartbeatMessage.toBytes();
            connectionHandler.sendMessage(messageBytes).get();
            
            LOGGER.fine("Heartbeat sent successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send heartbeat", e);
            throw new RuntimeException("Failed to send heartbeat", e);
        }
    }

    public int getCurrentSequenceNumber() {
        return sequenceNumber.get();
    }

    public void setHeartbeatInterval(long intervalSeconds) {
        // Would need to restart heartbeat scheduler with new interval
        LOGGER.info("Heartbeat interval change requested: " + intervalSeconds + "s (requires restart)");
    }

    public String generateSessionSubID() {
        int sessionId = sessionIdCounter.getAndIncrement();
        // SessionSubID is 4 bytes max, so use format like "S001", "S002", etc.
        this.sessionSubID = String.format("S%03d", sessionId);
        return this.sessionSubID;
    }

    public SessionState getSessionState() {
        return sessionState;
    }

    private void setSessionState(SessionState newState) {
        SessionState oldState = this.sessionState;
        this.sessionState = newState;
        LOGGER.info("Session state changed: " + oldState + " -> " + newState);
    }

    public String getSessionSubID() {
        return sessionSubID;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public Instant getLastHeartbeatTime() {
        return lastHeartbeatTime;
    }

    public boolean isActive() {
        return sessionState == SessionState.ACTIVE || 
               sessionState == SessionState.AUTHENTICATED;
    }

    public void shutdown() {
        stopHeartbeat();
        connectionHandler.shutdown();
    }
    
    private void handleLoginResponse(LoginResponseMessage response) {
        LOGGER.info("Handling login response: " + response);
        
        if (response.isAccepted()) {
            setSessionState(SessionState.ACTIVE);
            LOGGER.info("Session active - Login accepted");
        } else {
            setSessionState(SessionState.ERROR);
            LOGGER.warning("Login rejected: " + response.getLoginResponseText());
        }
    }
    
    private void handleLogoutResponse(LogoutResponseMessage response) {
        LOGGER.info("Handling logout response: " + response);
        setSessionState(SessionState.DISCONNECTED);
    }
    
    private void handleServerHeartbeat(ServerHeartbeatMessage heartbeat) {
        LOGGER.fine("Received server heartbeat - session alive");
        lastHeartbeatTime = Instant.now();
    }
}