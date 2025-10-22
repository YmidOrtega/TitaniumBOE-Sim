package com.boe.simulator.protocol.message;

import com.boe.simulator.connection.BoeConnectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BoeSessionManagerTest {

    @Mock
    private BoeConnectionHandler mockConnectionHandler;
    @Mock
    private ScheduledExecutorService mockHeartbeatScheduler;

    private BoeSessionManager sessionManager;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        sessionManager = new BoeSessionManager(mockConnectionHandler);
    }

    @Test
    void constructor_shouldInitializeWithDisconnectedState() {
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void login_shouldTransitionStatesAndSendLoginRequestOnSuccess() throws Exception {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> loginFuture = sessionManager.login("user", "testPass");
        loginFuture.join();

        verify(mockConnectionHandler).connect();
        verify(mockConnectionHandler, times(1)).sendMessage(any(byte[].class));

        assertEquals(SessionState.AUTHENTICATED, sessionManager.getSessionState());

        assertEquals("S001", sessionManager.getSessionSubID());

        assertEquals("user", sessionManager.getUsername());
        assertEquals("testPass", sessionManager.getPassword());
    }

    @Test
    void login_shouldSetErrorStateOnConnectionFailure() {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Connection failed");
        }));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sessionManager.login("user", "testPass").join(),
                "Expected login to throw RuntimeException on connection failure, but it didn't");
        assertTrue(thrown.getMessage().contains("Login failed"));
        assertEquals(SessionState.ERROR, sessionManager.getSessionState());
    }

    @Test
    void login_shouldSetErrorStateOnLoginRequestFailure() {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Login request failed");
        }));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sessionManager.login("user", "testPass").join(),
                "Expected login to throw RuntimeException on login request failure, but it didn't");
        assertTrue(thrown.getMessage().contains("Login failed"));
        assertEquals(SessionState.ERROR, sessionManager.getSessionState());
    }

    @Test
    void logout_shouldTransitionStatesAndSendLogoutRequestOnSuccess() throws Exception {

        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();
        assertEquals(SessionState.AUTHENTICATED, sessionManager.getSessionState());

        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> logoutFuture = sessionManager.logout();
        logoutFuture.join();

        verify(mockConnectionHandler, times(2)).sendMessage(any(byte[].class));
        verify(mockConnectionHandler).disconnect();
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void logout_shouldThrowRuntimeExceptionOnFailure() {

        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();

        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Disconnect failed");
        }));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sessionManager.logout().join(),
                "Expected logout to throw RuntimeException on disconnect failure, but it didn't");
        assertTrue(thrown.getMessage().contains("Logout failed"));

        assertNotEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void generateSessionSubID_shouldGenerateUniqueIDs() {
        String id1 = sessionManager.generateSessionSubID();
        String id2 = sessionManager.generateSessionSubID();
        assertNotNull(id1);
        assertNotNull(id2);
        assertNotEquals(id1, id2);
        assertTrue(id1.startsWith("S"));
        assertTrue(id2.startsWith("S"));
    }

    @Test
    void getSessionState_shouldReturnCurrentState() {
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void isActive_shouldReturnTrueForActiveAndAuthenticatedStates() {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();
        assertTrue(sessionManager.isActive());

        sessionManager = new BoeSessionManager(mockConnectionHandler);
        assertFalse(sessionManager.isActive());
    }

    @Test
    void shutdown_shouldStopHeartbeatAndShutdownConnectionHandler() {
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();

        sessionManager.shutdown();

        verify(mockConnectionHandler).shutdown();
    }
}
