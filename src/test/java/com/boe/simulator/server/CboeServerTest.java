package com.boe.simulator.server;

import com.boe.simulator.server.config.ServerConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CboeServerTest {

    @Mock
    private ServerConfiguration mockConfig;
    @Mock
    private ServerSocket mockServerSocket;
    @Mock
    private Socket mockSocket;
    @Mock
    private ExecutorService mockExecutor;

    private CboeServer cboeServer;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);
        when(mockConfig.getPort()).thenReturn(8080);
        when(mockConfig.getMaxConnections()).thenReturn(10);
        cboeServer = new CboeServer(mockConfig);
        setField(cboeServer, "serverSocket", mockServerSocket);
        setField(cboeServer, "clientExecutor", mockExecutor);
    }

    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void start_shouldStartServer() throws IOException {
        // Arrange
        // Mock the serverSocket.accept() to return a mockSocket once, then throw SocketTimeoutException
        when(mockServerSocket.accept())
                .thenReturn(mock(Socket.class))
                .thenThrow(new java.net.SocketTimeoutException());

        // Act
        cboeServer.start();

        // Assert that the server is running after start()
        assertTrue(cboeServer.isRunning());

        // Allow some time for the acceptor thread to run
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        cboeServer.stop();

        // Assert that the server is not running after stop()
        assertFalse(cboeServer.isRunning());
        
        
    }

    @Test
    void stop_shouldStopServer() throws IOException {
        // Arrange
        setField(cboeServer, "running", new java.util.concurrent.atomic.AtomicBoolean(true));

        // Act
        cboeServer.stop();

        // Assert
        assertFalse(cboeServer.isRunning());
        verify(mockServerSocket).close();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}