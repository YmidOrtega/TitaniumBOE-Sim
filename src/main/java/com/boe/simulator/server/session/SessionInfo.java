package com.boe.simulator.server.session;

import java.time.Instant;

import com.boe.simulator.protocol.message.SessionState;

public class SessionInfo {
    
    private final int connectionId;
    private final String username;
    private final String sessionSubID;
    private final String remoteAddress;
    private final SessionState state;
    private final Instant createdAt;
    private final int messagesReceived;
    private final int messagesSent;
    private final Instant lastHeartbeatReceived;
    
    public SessionInfo(ClientSession session) {
        this.connectionId = session.getConnectionId();
        this.username = session.getUsername();
        this.sessionSubID = session.getSessionSubID();
        this.remoteAddress = session.getRemoteAddress();
        this.state = session.getState();
        this.createdAt = session.getCreatedAt();
        this.messagesReceived = session.getMessagesReceived();
        this.messagesSent = session.getMessagesSent();
        this.lastHeartbeatReceived = session.getLastHeartbeatReceived();
    }
    
    // Getters
    public int getConnectionId() { return connectionId; }
    public String getUsername() { return username; }
    public String getSessionSubID() { return sessionSubID; }
    public String getRemoteAddress() { return remoteAddress; }
    public SessionState getState() { return state; }
    public Instant getCreatedAt() { return createdAt; }
    public int getMessagesReceived() { return messagesReceived; }
    public int getMessagesSent() { return messagesSent; }
    public Instant getLastHeartbeatReceived() { return lastHeartbeatReceived; }
    
    public long getDurationSeconds() {
        return java.time.Duration.between(createdAt, Instant.now()).getSeconds();
    }
    
    @Override
    public String toString() {
        return "SessionInfo{" +
                "id=" + connectionId +
                ", user='" + username + '\'' +
                ", session='" + sessionSubID + '\'' +
                ", state=" + state +
                ", duration=" + getDurationSeconds() + "s" +
                ", msgRx=" + messagesReceived +
                ", msgTx=" + messagesSent +
                '}';
    }
}
