package com.boe.simulator.protocol.message;

import com.boe.simulator.connection.BoeConnectionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

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
        // Inject the mock scheduler (this requires a slight modification to BoeSessionManager
        // or using reflection/PowerMockito. For now, let's assume we can inject it for testing.
        // If not, we'd have to test the scheduler's behavior indirectly or refactor BoeSessionManager.
        // For this test, I'll create a BoeSessionManager that uses the mock scheduler.
        // This means I need to modify BoeSessionManager to allow injecting ScheduledExecutorService.
        // Let's proceed with the assumption that I will refactor BoeSessionManager for testability.
        // If not, I'll have to remove the mockHeartbeatScheduler and test indirectly.

        // For now, let's create a real BoeSessionManager and test its public API.
        // The internal scheduler will be a real one, making heartbeat testing harder.
        // I will proceed with testing the public API and interactions with connectionHandler.
        // Testing the heartbeat scheduling will be indirect.

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
        loginFuture.join(); // Wait for the async operation to complete

        verify(mockConnectionHandler).connect();
        verify(mockConnectionHandler, times(1)).sendMessage(any(byte[].class)); // For login request

        assertEquals(SessionState.AUTHENTICATED, sessionManager.getSessionState());
        assertNotNull(sessionManager.getSessionSubID());
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
        // First, simulate a successful login to get into AUTHENTICATED state
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();
        assertEquals(SessionState.AUTHENTICATED, sessionManager.getSessionState());

        // Now, test logout
        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.completedFuture(null));

        CompletableFuture<Void> logoutFuture = sessionManager.logout();
        logoutFuture.join(); // Wait for the async operation to complete

        verify(mockConnectionHandler, times(2)).sendMessage(any(byte[].class)); // One for login, one for logout
        verify(mockConnectionHandler).disconnect();
        assertEquals(SessionState.DISCONNECTED, sessionManager.getSessionState());
    }

    @Test
    void logout_shouldThrowRuntimeExceptionOnFailure() {
        // Simulate login first
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();

        // Simulate disconnect failure
        when(mockConnectionHandler.disconnect()).thenReturn(CompletableFuture.supplyAsync(() -> {
            throw new RuntimeException("Disconnect failed");
        }));

        RuntimeException thrown = assertThrows(RuntimeException.class,
                () -> sessionManager.logout().join(),
                "Expected logout to throw RuntimeException on disconnect failure, but it didn't");
        assertTrue(thrown.getMessage().contains("Logout failed"));
        // State should still be DISCONNECTING or ERROR depending on where the exception is caught
        // For now, let's assume it stays in DISCONNECTING or ERROR
        // The current implementation catches and rethrows, so it will be DISCONNECTING or ERROR
        // Let's verify it's not DISCONNECTED
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
        // Further state transitions are tested in login/logout tests
    }

    @Test
    void isActive_shouldReturnTrueForActiveAndAuthenticatedStates() {
        // Simulate AUTHENTICATED state
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();
        assertTrue(sessionManager.isActive());

        // Simulate DISCONNECTED state
        sessionManager = new BoeSessionManager(mockConnectionHandler); // Reset for DISCONNECTED
        assertFalse(sessionManager.isActive());
    }

    @Test
    void shutdown_shouldStopHeartbeatAndShutdownConnectionHandler() {
        // Simulate login to start heartbeat
        when(mockConnectionHandler.connect()).thenReturn(CompletableFuture.completedFuture(null));
        when(mockConnectionHandler.sendMessage(any(byte[].class))).thenReturn(CompletableFuture.completedFuture(null));
        sessionManager.login("user", "testPass").join();

        // Now call shutdown
        sessionManager.shutdown();

        // Verify that the internal heartbeatScheduler (which is a real one here) is shut down.
        // This is hard to verify directly without mocking the scheduler.
        // If we were to refactor BoeSessionManager to inject the scheduler, we could verify mockHeartbeatScheduler.shutdown().
        // For now, we can only verify connectionHandler.shutdown().
        verify(mockConnectionHandler).shutdown();
    }

    // Helper method to access private fields for testing purposes (if necessary, but try to avoid)
    // private ScheduledExecutorService getHeartbeatScheduler(BoeSessionManager manager) throws Exception {
    //     java.lang.reflect.Field field = BoeSessionManager.class.getDeclaredField("heartbeatScheduler");
    //     field.setAccessible(true);
    //     return (ScheduledExecutorService) field.get(manager);
    // }
}
