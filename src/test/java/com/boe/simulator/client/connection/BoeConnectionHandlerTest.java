package com.boe.simulator.client.connection;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.MockitoAnnotations;

import com.boe.simulator.protocol.message.BoeMessage;

class BoeConnectionHandlerTest {

    @Mock
    private Socket mockSocket;
    @Mock
    private InputStream mockInputStream;
    @Mock
    private OutputStream mockOutputStream;

    private BoeConnectionHandler connectionHandler;
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() throws IOException {
        closeable = MockitoAnnotations.openMocks(this);
        connectionHandler = new BoeConnectionHandler("localhost", 12345);

        when(mockSocket.getInputStream()).thenReturn(mockInputStream);
        when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);
        when(mockSocket.isClosed()).thenReturn(false);

        setField(connectionHandler, "socket", mockSocket);
        setField(connectionHandler, "inputStream", mockInputStream);
        setField(connectionHandler, "outputStream", mockOutputStream);
        setField(connectionHandler, "running", true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        connectionHandler.shutdown();
    }

    @Test
    void constructor_shouldSetHostAndPort() {
        // Arrange
        String host = "testHost";
        int port = 8080;

        // Act
        BoeConnectionHandler handler = new BoeConnectionHandler(host, port);

        // Assert
        assertEquals(host, getField(handler, "host"));
        assertEquals(port, getField(handler, "port"));
    }

    @Test
    void connect_shouldNotReconnect_whenAlreadyConnected() throws Exception {
        // Arrange
        assertTrue(connectionHandler.isConnected());

        // Act
        connectionHandler.connect().join();

        // Assert
        assertTrue(connectionHandler.isConnected());
        // verify that a new socket is not created
        verify(mockSocket, never()).close();
    }

    @Test
    void disconnect_shouldCloseAllResources() throws Exception {
        // Arrange & Act
        connectionHandler.disconnect().join();

        // Assert
        verify(mockInputStream).close();
        verify(mockOutputStream).close();
        verify(mockSocket).close();
    }
    
    @Test
    void disconnect_shouldHandleIOExceptionsGracefully() {
        // Arrange
        assertDoesNotThrow(() -> {
            doThrow(new IOException("Input stream close error")).when(mockInputStream).close();
            doThrow(new IOException("Output stream close error")).when(mockOutputStream).close();
            doThrow(new IOException("Socket close error")).when(mockSocket).close();

            // Act
            connectionHandler.disconnect().join();

            // Assert
            verify(mockInputStream).close();
            verify(mockOutputStream).close();
            verify(mockSocket).close();
        });
    }

    @Test
    void sendMessage_shouldWritePayloadToOutputStream() {
        // Arrange
        byte[] payload = {0x05, 0x06, 0x07};

        // Act
        connectionHandler.sendMessage(payload).join();

        // Assert
        assertDoesNotThrow(() -> {
            verify(mockOutputStream).write(any(byte[].class));
            verify(mockOutputStream).flush();
        });
    }

    @Test
    void sendMessage_shouldThrowException_whenNotConnected() {
        // Arrange
        connectionHandler.disconnect().join();
        when(mockSocket.isClosed()).thenReturn(true);
        byte[] payload = {0x01, 0x02};

        // Act & Assert
        CompletionException thrown = assertThrows(CompletionException.class,
                () -> connectionHandler.sendMessage(payload).join());
        assertTrue(thrown.getCause() instanceof RuntimeException);
        assertTrue(thrown.getCause().getCause() instanceof IOException);
        assertTrue(thrown.getCause().getCause().getMessage().contains("Not connected"));
    }

    @Test
    void sendMessage_shouldThrowRuntimeException_whenWriteFails() throws IOException {
        // Arrange
        byte[] payload = {0x01, 0x02, 0x03};
        doThrow(new IOException("Write error")).when(mockOutputStream).write(any(byte[].class));

        // Act & Assert
        CompletionException thrown = assertThrows(CompletionException.class, () -> connectionHandler.sendMessage(payload).join());
        assertTrue(thrown.getCause() instanceof RuntimeException);
        assertTrue(thrown.getCause().getCause() instanceof IOException);
        assertTrue(thrown.getCause().getCause().getMessage().contains("Write error"));
    }

    @Test
    void sendMessageRaw_shouldWriteRawBytesToOutputStream() throws IOException {
        // Arrange
        byte[] rawMessage = {0x01, 0x02, 0x03, 0x04, 0x05};

        // Act
        connectionHandler.sendMessageRaw(rawMessage).join();

        // Assert
        verify(mockOutputStream).write(rawMessage);
        verify(mockOutputStream).flush();
    }

    @Test
    void startListener_shouldDoNothing_whenNotConnected() {
        // Arrange
        connectionHandler.disconnect().join();
        when(mockSocket.isClosed()).thenReturn(true);

        // Act & Assert
        assertDoesNotThrow(() -> connectionHandler.startListener().join());
    }

    @Test
    void startListener_shouldProcessMessages_untilStopped() throws Exception {
        // Arrange
        InputStream testInputStream = createTestInputStream();
        setField(connectionHandler, "inputStream", testInputStream);
        BoeConnectionHandler spyHandler = spy(connectionHandler);
        doNothing().when(spyHandler).processMessage(any(BoeMessage.class));

        // Act
        CompletableFuture<Void> listenerFuture = spyHandler.startListener();
        TimeUnit.MILLISECONDS.sleep(200); // allow listener to run
        spyHandler.stopListener();
        listenerFuture.get(1, TimeUnit.SECONDS); // wait for listener to finish

        // Assert
        verify(spyHandler, times(2)).processMessage(any(BoeMessage.class));
    }

    @Test
    void stopListener_shouldSetRunningToFalseAndStopListener() throws Exception {
        // Arrange
        // Use a mock stream that blocks to ensure the listener is running
        when(mockInputStream.read(any(), anyInt(), anyInt())).thenAnswer(inv -> {
            Thread.sleep(100);
            return -1; // EOF
        });
        CompletableFuture<Void> listenerFuture = connectionHandler.startListener();
        
        // Act
        connectionHandler.stopListener();
        listenerFuture.get(1, TimeUnit.SECONDS);

        // Assert
        assertFalse((boolean) getField(connectionHandler, "running"));
    }

    @Test
    void isConnected_shouldReturnTrue_whenSocketIsNotNullAndNotClosed() {
        // Arrange & Act & Assert
        assertTrue(connectionHandler.isConnected());
    }

    @Test
    void isConnected_shouldReturnFalse_whenSocketIsClosed() {
        // Arrange
        when(mockSocket.isClosed()).thenReturn(true);

        // Act & Assert
        assertFalse(connectionHandler.isConnected());
    }

    @Test
    void isConnected_shouldReturnFalse_whenSocketIsNull() {
        // Arrange
        setField(connectionHandler, "socket", null);

        // Act & Assert
        assertFalse(connectionHandler.isConnected());
    }

    @Test
    void startListener_shouldStopGracefully_whenIOExceptionOccurs() throws IOException {
        // Arrange
        when(mockInputStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("Test exception"));

        // Act
        CompletableFuture<Void> listenerFuture = connectionHandler.startListener();

        // Assert
        assertDoesNotThrow(() -> listenerFuture.get(1, TimeUnit.SECONDS));
        assertFalse((boolean) getField(connectionHandler, "running"));
    }

    @Test
    void shutdown_shouldShutdownExecutorService() {
        // Arrange, Act & Assert
        assertDoesNotThrow(() -> connectionHandler.shutdown());
    }

    // --- Helper Methods ---

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }

    private Object getField(Object target, String fieldName) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(target);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to get field '" + fieldName + "': " + e.getMessage());
            return null;
        }
    }

    private InputStream createTestInputStream() throws IOException {
        byte[] msg1Data = new byte[]{
                (byte) 0xBA, (byte) 0xBA, 8, 0, 1, 0, 0, 0, 0, 0
        };
        byte[] msg2Data = new byte[]{
                (byte) 0xBA, (byte) 0xBA, 8, 0, 2, 0, 0, 0, 0, 0
        };
        ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();
        combinedStream.write(msg1Data);
        combinedStream.write(msg2Data);
        return new ByteArrayInputStream(combinedStream.toByteArray());
    }
}