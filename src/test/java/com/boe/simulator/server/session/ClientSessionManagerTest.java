package com.boe.simulator.server.session;

import com.boe.simulator.connection.ClientConnectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSessionManagerTest {

    @Mock
    private ClientConnectionHandler mockHandler1;
    @Mock
    private ClientSession mockSession1;

    @Mock
    private ClientConnectionHandler mockHandler2;
    @Mock
    private ClientSession mockSession2;

    private ClientSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionManager = new ClientSessionManager();

        // Setup for handler 1
        when(mockHandler1.getSession()).thenReturn(mockSession1);
        when(mockSession1.getConnectionId()).thenReturn(1);
        when(mockSession1.getUsername()).thenReturn("user1");

        // Setup for handler 2
        when(mockHandler2.getSession()).thenReturn(mockSession2);
        when(mockSession2.getConnectionId()).thenReturn(2);
        when(mockSession2.getUsername()).thenReturn("user2");
    }

    @Test
    void registerHandler_shouldAddHandlerAndIncrementTotalConnections() {
        // Arrange
        SessionStatistics stats = sessionManager.getStatistics();
        assertEquals(0, stats.getTotalConnections());

        // Act
        sessionManager.registerHandler(mockHandler1);

        // Assert
        assertEquals(mockHandler1, sessionManager.getHandler(1));
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertEquals(1, stats.getTotalConnections());
    }

    @Test
    void unregisterHandler_shouldRemoveHandlerAndUsername() {
        // Arrange
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerUsername(mockHandler1, "user1");
        assertEquals(1, sessionManager.getActiveSessionCount());
        assertNotNull(sessionManager.getHandlerByUsername("user1"));

        // Act
        sessionManager.unregisterHandler(mockHandler1);

        // Assert
        assertNull(sessionManager.getHandler(1));
        assertNull(sessionManager.getHandlerByUsername("user1"));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    void registerUsername_shouldAssociateUsernameAndIncrementSuccessfulLogins() {
        // Arrange
        SessionStatistics stats = sessionManager.getStatistics();
        assertEquals(0, stats.getSuccessfulLogins());

        // Act
        sessionManager.registerUsername(mockHandler1, "user1");

        // Assert
        assertEquals(mockHandler1, sessionManager.getHandlerByUsername("user1"));
        assertEquals(1, stats.getSuccessfulLogins());
    }

    @Test
    void broadcastMessage_shouldSendMessageOnlyToAuthenticatedHandlers() throws Exception {
        // Arrange
        when(mockSession1.isAuthenticated()).thenReturn(true);
        when(mockSession2.isAuthenticated()).thenReturn(false);
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerHandler(mockHandler2);
        byte[] message = {0x01, 0x02, 0x03};

        // Act
        sessionManager.broadcastMessage(message);

        // Assert
        verify(mockHandler1).sendMessage(message);
        verify(mockHandler2, never()).sendMessage(message);
    }

    @Test
    void broadcastToUsers_shouldSendMessageOnlyToSpecifiedUsers() throws Exception {
        // Arrange
        when(mockSession1.isAuthenticated()).thenReturn(true);
        when(mockSession2.isAuthenticated()).thenReturn(true);
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerUsername(mockHandler1, "user1");
        sessionManager.registerHandler(mockHandler2);
        sessionManager.registerUsername(mockHandler2, "user2");
        byte[] message = {0x01, 0x02, 0x03};

        // Act
        sessionManager.broadcastToUsers(message, List.of("user1"));

        // Assert
        verify(mockHandler1).sendMessage(message);
        verify(mockHandler2, never()).sendMessage(message);
    }

    @Test
    void broadcastMessage_shouldContinueWhenOneHandlerFails() throws Exception {
        // Arrange
        when(mockSession1.isAuthenticated()).thenReturn(true);
        when(mockSession2.isAuthenticated()).thenReturn(true);
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerHandler(mockHandler2);
        byte[] message = {0x01, 0x02, 0x03};
        doThrow(new IOException("Test failure")).when(mockHandler1).sendMessage(message);

        // Act
        sessionManager.broadcastMessage(message);

        // Assert
        verify(mockHandler1).sendMessage(message); // Still attempted
        verify(mockHandler2).sendMessage(message); // Should still be called
    }

    @Test
    void disconnectUser_shouldStopHandler_whenUserExists() {
        // Arrange
        sessionManager.registerUsername(mockHandler1, "user1");

        // Act
        boolean result = sessionManager.disconnectUser("user1");

        // Assert
        assertTrue(result);
        verify(mockHandler1).stop();
    }

    @Test
    void disconnectUser_shouldReturnFalse_whenUserDoesNotExist() {
        // Act
        boolean result = sessionManager.disconnectUser("nonexistent");

        // Assert
        assertFalse(result);
    }

    @Test
    void disconnectAll_shouldStopAllHandlers() {
        // Arrange
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerHandler(mockHandler2);

        // Act
        sessionManager.disconnectAll();

        // Assert
        verify(mockHandler1).stop();
        verify(mockHandler2).stop();
    }

    @Test
    void getAuthenticatedSessionCount_shouldReturnCorrectCount() {
        // Arrange
        when(mockSession1.isAuthenticated()).thenReturn(true);
        when(mockSession2.isAuthenticated()).thenReturn(false);
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerHandler(mockHandler2);

        // Act
        int count = sessionManager.getAuthenticatedSessionCount();

        // Assert
        assertEquals(1, count);
    }


    @Test
    void getSessionInfoList_shouldReturnCorrectInfo() {
        // Arrange
        sessionManager.registerHandler(mockHandler1);
        sessionManager.registerHandler(mockHandler2);

        // Act
        List<SessionInfo> sessionInfoList = sessionManager.getSessionInfoList();

        // Assert
        assertEquals(2, sessionInfoList.size());
        assertTrue(sessionInfoList.stream().anyMatch(info -> info.getConnectionId() == 1));
        assertTrue(sessionInfoList.stream().anyMatch(info -> info.getConnectionId() == 2));
    }
}