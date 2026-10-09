package com.boe.simulator.server.session;

import com.boe.simulator.protocol.message.SessionState;
import com.boe.simulator.protocol.message.ReturnBitfields;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSession {

    private final int connectionId;
    private final String remoteAddress;
    private final Instant createdAt;

    // Session identifiers
    private String username;
    private String sessionSubID;
    private volatile ReturnBitfields returnBitfields;

    // State management
    private volatile SessionState state;

    // Heartbeat tracking
    private volatile Instant lastHeartbeatSent;
    private volatile Instant lastHeartbeatReceived;
    private volatile long lastInboundNanos = System.nanoTime();
    private volatile long lastOutboundNanos = System.nanoTime();

    // Statistics
    private final AtomicInteger messagesReceived;
    private final AtomicInteger messagesSent;

    public ClientSession(int connectionId, String remoteAddress) {
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.createdAt = Instant.now();
        this.state = SessionState.CONNECTED;
        this.returnBitfields = ReturnBitfields.empty();
        this.messagesReceived = new AtomicInteger(0);
        this.messagesSent = new AtomicInteger(0);
    }

    // Message counters
    public void incrementMessagesReceived() {
        messagesReceived.incrementAndGet();
    }

    public void incrementMessagesSent() {
        messagesSent.incrementAndGet();
    }

    public int getMessagesReceived() {
        return messagesReceived.get();
    }

    public int getMessagesSent() {
        return messagesSent.get();
    }

    public void markInbound() {
        this.lastInboundNanos = System.nanoTime();
    }

    public void markOutbound() {
        this.lastOutboundNanos = System.nanoTime();
    }

    public long nanosSinceInbound() {
        return System.nanoTime() - lastInboundNanos;
    }

    public long nanosSinceOutbound() {
        return System.nanoTime() - lastOutboundNanos;
    }

    // Heartbeat tracking
    public void updateHeartbeatSent() {
        this.lastHeartbeatSent = Instant.now();
    }

    public void updateHeartbeatReceived() {
        this.lastHeartbeatReceived = Instant.now();
    }

    // State management
    public boolean isAuthenticated() {
        return state == SessionState.AUTHENTICATED || state == SessionState.ACTIVE;
    }

    public boolean isActive() {
        return state == SessionState.ACTIVE;
    }

    public void terminate() {
        state = SessionState.DISCONNECTED;
    }

    // Getters and Setters
    public int getConnectionId() { return connectionId; }
    public String getRemoteAddress() { return remoteAddress; }
    public Instant getCreatedAt() { return createdAt; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getSessionSubID() { return sessionSubID; }
    public void setSessionSubID(String sessionSubID) { this.sessionSubID = sessionSubID; }
    public ReturnBitfields getReturnBitfields() { return returnBitfields; }
    public void setReturnBitfields(ReturnBitfields returnBitfields) {
        this.returnBitfields = returnBitfields != null ? returnBitfields : ReturnBitfields.empty();
    }
    public SessionState getState() { return state; }
    public void setState(SessionState state) { this.state = state; }
    public Instant getLastHeartbeatSent() { return lastHeartbeatSent; }
    public Instant getLastHeartbeatReceived() { return lastHeartbeatReceived; }

    @Override
    public String toString() {
        return "ClientSession{" +
                "id=" + connectionId +
                ", user='" + username + '\'' +
                ", sessionSubID='" + sessionSubID + '\'' +
                ", state=" + state +
                ", remote=" + remoteAddress +
                ", msgRx=" + messagesReceived.get() +
                ", msgTx=" + messagesSent.get() +
                '}';
    }
}
