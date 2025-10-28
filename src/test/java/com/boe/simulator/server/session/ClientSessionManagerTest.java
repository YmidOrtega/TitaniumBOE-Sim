package com.boe.simulator.server.session;

import com.boe.simulator.connection.ClientConnectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClientSessionManagerTest {

    @Mock
    private ClientConnectionHandler mockHandler;
    @Mock
    private ClientSession mockSession;

    private ClientSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockHandler.getSession()).thenReturn(mockSession);
        when(mockSession.getConnectionId()).thenReturn(1);
        sessionManager = new ClientSessionManager();
    }

    @Test
    void registerHandler_shouldAddHandler() {
        // Act
        sessionManager.registerHandler(mockHandler);

        // Assert
        assertEquals(mockHandler, sessionManager.getHandler(1));
        assertEquals(1, sessionManager.getActiveSessionCount());
    }

    @Test
    void unregisterHandler_shouldRemoveHandler() {
        // Arrange
        sessionManager.registerHandler(mockHandler);

        // Act
        sessionManager.unregisterHandler(mockHandler);

        // Assert
        assertNull(sessionManager.getHandler(1));
        assertEquals(0, sessionManager.getActiveSessionCount());
    }

    @Test
    void registerUsername_shouldAssociateUsernameWithHandler() {
        // Arrange
        when(mockSession.getUsername()).thenReturn("testuser");

        // Act
        sessionManager.registerUsername(mockHandler, "testuser");

        // Assert
        assertEquals(mockHandler, sessionManager.getHandlerByUsername("testuser"));
    }

    @Test
    void broadcastMessage_shouldSendMessageToAuthenticatedHandlers() throws Exception {
        // Arrange
        when(mockSession.isAuthenticated()).thenReturn(true);
        sessionManager.registerHandler(mockHandler);
        byte[] message = {0x01, 0x02, 0x03};

        // Act
        sessionManager.broadcastMessage(message);

        // Assert
        verify(mockHandler).sendMessage(message);
    }

    @Test
    void broadcastMessage_shouldNotSendMessageToUnauthenticatedHandlers() throws Exception {
        // Arrange
        when(mockSession.isAuthenticated()).thenReturn(false);
        sessionManager.registerHandler(mockHandler);
        byte[] message = {0x01, 0x02, 0x03};

        // Act
        sessionManager.broadcastMessage(message);

        // Assert
        verify(mockHandler, never()).sendMessage(message);
    }

    @Test
    void disconnectUser_shouldStopHandler() {
        // Arrange
        when(mockSession.getUsername()).thenReturn("testuser");
        sessionManager.registerUsername(mockHandler, "testuser");

        // Act
        sessionManager.disconnectUser("testuser");

        // Assert
        verify(mockHandler).stop();
    }

    @Test
    void disconnectAll_shouldStopAllHandlers() {
        // Arrange
        ClientConnectionHandler anotherHandler = mock(ClientConnectionHandler.class);
        ClientSession anotherSession = mock(ClientSession.class);
        when(anotherHandler.getSession()).thenReturn(anotherSession);
        when(anotherSession.getConnectionId()).thenReturn(2);
        sessionManager.registerHandler(mockHandler);
        sessionManager.registerHandler(anotherHandler);

        // Act
        sessionManager.disconnectAll();

        // Assert
        verify(mockHandler).stop();
        verify(anotherHandler).stop();
    }
}