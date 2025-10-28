package com.boe.simulator.protocol.message;

import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.Mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.boe.simulator.connection.BoeConnectionHandler;
import com.boe.simulator.connection.BoeMessageListener;

class BoeSessionManagerTest {

    @Mock
    private BoeConnectionHandler mockConnectionHandler;

    private BoeSessionManager sessionManager;
    
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        sessionManager = new BoeSessionManager(mockConnectionHandler);
    }

    @Test
    void constructor_shouldSetMessageListenerOnConnectionHandler() {
        // Arrange
        verify(mockConnectionHandler).setMessageListener(any(BoeMessageListener.class));
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void newSessionManager_shouldBeInDisconnectedState() {
        // Arrange, Act & Assert
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void login_shouldTransitionToAuthenticated_whenConnectionAndLoginSucceed() {
        // Arrange
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        sessionManager.login("user", "testPass").join();

        // Assert
        verify(mockConnectionHandler).connect();
        verify(mockConnectionHandler).sendMessage(any(byte[].class));
        assertEquals(SessionState.AUTHENTICATED, sessionManager.getSessionState());
        assertEquals("user", sessionManager.getUsername());
        assertEquals("testPass", sessionManager.getPassword());
    }

    @Test
    void login_shouldTransitionToErrorState_whenConnectionFails() {
        // Arrange
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Connection failed");
        }));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> sessionManager.login("user", "testPass").join());
        assertEquals(SessionState.ERROR, sessionManager.getSessionState());
    }

    @Test
    void login_shouldTransitionToErrorState_whenLoginRequestFails() {
        // Arrange
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Login request failed");
        }));

        // Act & Assert
        assertThrows(RuntimeException.class, () -> sessionManager.login("user", "testPass").join());
        assertEquals(SessionState.ERROR, sessionManager.getSessionState());
    }

    @Test
    void logout_shouldTransitionToDisconnected_whenLogoutSucceeds() {
        // Arrange
        givenLoggedInSession();
        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));

        // Act
        sessionManager.logout().join();

        // Assert
        verify(mockConnectionHandler).disconnect();
        verify(mockConnectionHandler, times(2)).sendMessage(any(byte[].class));
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void logout_shouldNotChangeState_whenDisconnectFails() {
        // Arrange
        givenLoggedInSession();
        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Disconnect failed");
        }));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));


        // Act & Assert
        assertThrows(RuntimeException.class, () -> sessionManager.logout().join());
        assertNotEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void generateSessionSubID_shouldReturnUniqueIDs() {
        // Arrange
        String id1 = sessionManager.generateSessionSubID();
        
        // Act
        String id2 = sessionManager.generateSessionSubID();

        // Assert
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("S"));
        assertTrue(id2.startsWith("S"));
    }

    @Test
    void isActive_shouldReturnTrue_whenStateIsAuthenticated() {
        // Arrange
        givenLoggedInSession();

        // Act & Assert
        assertTrue(sessionManager.isActive());
    }

    

    @Test
    void isActive_shouldReturnFalse_whenStateIsDisconnected() {
        // Arrange, Act & Assert
        assertFalse(sessionManager.isActive());
    }

    @Test
    void shutdown_shouldCallConnectionHandlerShutdown() {
        // Arrange
        givenLoggedInSession();

        // Act
        sessionManager.shutdown();

        // Assert
        verify(mockConnectionHandler).shutdown();
    }

    private void givenLoggedInSession() {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();
    }

    private Object getField(Object target, String fieldName) throws NoSuchFieldException, IllegalAccessException {
        java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }
}