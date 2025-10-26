package com.boe.simulator.server.session;

import com.boe.simulator.protocol.message.SessionState;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSession {

    private final int connectionId;
    private final String remoteAddress;
    private final Instant createdAt;

    // Session identifiers
    private String username;
    private String sessionSubID;
    private byte matchingUnit;

    // State management
    private volatile SessionState state;

    // Sequence tracking
    private final AtomicInteger sentSequenceNumber;
    private final AtomicInteger receivedSequenceNumber;

    // Heartbeat tracking
    private volatile Instant lastHeartbeatSent;
    private volatile Instant lastHeartbeatReceived;

    // Statistics
    private final AtomicInteger messagesReceived;
    private final AtomicInteger messagesSent;

    public ClientSession(int connectionId, String remoteAddress) {
        this.connectionId = connectionId;
        this.remoteAddress = remoteAddress;
        this.createdAt = Instant.now();
        this.state = SessionState.CONNECTED;
        this.matchingUnit = 0;
        this.sentSequenceNumber = new AtomicInteger(1);
        this.receivedSequenceNumber = new AtomicInteger(0);
        this.messagesReceived = new AtomicInteger(0);
        this.messagesSent = new AtomicInteger(0);
    }

    // Sequence number management
    public int getNextSentSequenceNumber() {
        return sentSequenceNumber.getAndIncrement();
    }

    public int getCurrentSentSequenceNumber() {
        return sentSequenceNumber.get();
    }

    public void updateReceivedSequenceNumber(int seqNum) {
        receivedSequenceNumber.set(seqNum);
    }

    public int getLastReceivedSequenceNumber() {
        return receivedSequenceNumber.get();
    }

    public boolean isSequenceInOrder(int seqNum) {
        return seqNum == receivedSequenceNumber.get() + 1;
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

    // Heartbeat tracking
    public void updateHeartbeatSent() {
        this.lastHeartbeatSent = Instant.now();
    }

    public void updateHeartbeatReceived() {
        this.lastHeartbeatReceived = Instant.now();
    }

    public boolean isHeartbeatExpired(long timeoutSeconds) {
        if (lastHeartbeatReceived == null) return false;
        return Duration.between(lastHeartbeatReceived, Instant.now()).getSeconds() > timeoutSeconds;
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
    public byte getMatchingUnit() { return matchingUnit; }
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
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
