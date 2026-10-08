package com.boe.simulator.server.session;

import com.boe.simulator.protocol.message.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ClientSessionTest {

    private ClientSession session;

    @BeforeEach
    void setUp() {
        session = new ClientSession(1, "localhost");
    }

    @Test
    void incrementMessagesReceived_shouldIncrementCount() {
        // Arrange
        int initialCount = session.getMessagesReceived();

        // Act
        session.incrementMessagesReceived();

        // Assert
        assertEquals(initialCount + 1, session.getMessagesReceived());
    }

    @Test
    void incrementMessagesSent_shouldIncrementCount() {
        // Arrange
        int initialCount = session.getMessagesSent();

        // Act
        session.incrementMessagesSent();

        // Assert
        assertEquals(initialCount + 1, session.getMessagesSent());
    }

    @Test
    void updateHeartbeatSent_shouldUpdateTimestamp() {
        // Act
        session.updateHeartbeatSent();

        // Assert
        assertNotNull(session.getLastHeartbeatSent());
    }

    @Test
    void updateHeartbeatReceived_shouldUpdateTimestamp() {
        // Act
        session.updateHeartbeatReceived();

        // Assert
        assertNotNull(session.getLastHeartbeatReceived());
    }

    @Test
    void isAuthenticated_shouldReturnTrue_forAuthenticatedAndActiveStates() {
        // Arrange
        session.setState(SessionState.AUTHENTICATED);
        assertTrue(session.isAuthenticated());

        // Act
        session.setState(SessionState.ACTIVE);

        // Assert
        assertTrue(session.isAuthenticated());
    }

    @Test
    void isAuthenticated_shouldReturnFalse_forOtherStates() {
        // Arrange
        session.setState(SessionState.CONNECTING);

        // Assert
        assertFalse(session.isAuthenticated());
    }

    @Test
    void terminate_shouldSetStateToDisconnected() {
        // Act
        session.terminate();

        // Assert
        assertEquals(SessionState.DISCONNECTED, session.getState());
    }
}