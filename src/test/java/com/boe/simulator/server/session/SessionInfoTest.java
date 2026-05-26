package com.boe.simulator.server.session;

import com.boe.simulator.protocol.message.SessionState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class SessionInfoTest {

    @Mock
    private ClientSession mockSession;

    private SessionInfo sessionInfo;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockSession.getConnectionId()).thenReturn(1);
        when(mockSession.getUsername()).thenReturn("testuser");
        when(mockSession.getSessionSubID()).thenReturn("sub1");
        when(mockSession.getRemoteAddress()).thenReturn("localhost");
        when(mockSession.getState()).thenReturn(SessionState.ACTIVE);
        when(mockSession.getCreatedAt()).thenReturn(Instant.now());
        when(mockSession.getMessagesReceived()).thenReturn(10);
        when(mockSession.getMessagesSent()).thenReturn(5);
        when(mockSession.getLastHeartbeatReceived()).thenReturn(Instant.now());

        sessionInfo = SessionInfo.from(mockSession);
    }

    @Test
    void constructor_shouldInitializeFieldsFromSession() {
        // Assert
        assertEquals(1, sessionInfo.connectionId());
        assertEquals("testuser", sessionInfo.username());
        assertEquals("sub1", sessionInfo.sessionSubID());
        assertEquals("localhost", sessionInfo.remoteAddress());
        assertEquals(SessionState.ACTIVE, sessionInfo.state());
        assertNotNull(sessionInfo.createdAt());
        assertEquals(10, sessionInfo.messagesReceived());
        assertEquals(5, sessionInfo.messagesSent());
        assertNotNull(sessionInfo.lastHeartbeatReceived());
    }

    @Test
    void getDurationSeconds_shouldReturnPositiveValue() throws InterruptedException {
        // Arrange
        when(mockSession.getCreatedAt()).thenReturn(Instant.now().minusSeconds(10));
        sessionInfo = SessionInfo.from(mockSession);

        // Act
        long duration = sessionInfo.getDurationSeconds();

        // Assert
        assertTrue(duration >= 10);
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        // Act
        String s = sessionInfo.toString();

        // Assert
        assertTrue(s.contains("id=1"));
        assertTrue(s.contains("user='testuser'"));
        assertTrue(s.contains("session='sub1'"));
        assertTrue(s.contains("state=ACTIVE"));
        assertTrue(s.contains("msgRx=10"));
        assertTrue(s.contains("msgTx=5"));
    }
}