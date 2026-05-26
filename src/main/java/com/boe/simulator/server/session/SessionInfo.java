package com.boe.simulator.server.session;

import java.time.Duration;
import java.time.Instant;

import com.boe.simulator.protocol.message.SessionState;

public record SessionInfo(
        int connectionId,
        String username,
        String sessionSubID,
        String remoteAddress,
        SessionState state,
        Instant createdAt,
        int messagesReceived,
        int messagesSent,
        Instant lastHeartbeatReceived
) {
    public static SessionInfo from(ClientSession session) {
        return new SessionInfo(
                session.getConnectionId(),
                session.getUsername(),
                session.getSessionSubID(),
                session.getRemoteAddress(),
                session.getState(),
                session.getCreatedAt(),
                session.getMessagesReceived(),
                session.getMessagesSent(),
                session.getLastHeartbeatReceived()
        );
    }

    public long getDurationSeconds() {
        return Duration.between(createdAt, Instant.now()).getSeconds();
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
