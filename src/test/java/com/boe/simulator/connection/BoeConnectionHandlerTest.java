package com.boe.simulator.connection;

import com.boe.simulator.protocol.message.BoeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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

        when(mockSocket.getInputStream()).thenReturn(mockInputStream);
        when(mockSocket.getOutputStream()).thenReturn(mockOutputStream);
        when(mockSocket.isClosed()).thenReturn(false);

        connectionHandler = new BoeConnectionHandler("localhost", 12345);

        injectField("socket", mockSocket);
        injectField("inputStream", mockInputStream);
        injectField("outputStream", mockOutputStream);
        injectField("running", true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (closeable != null) {
            closeable.close();
        }
        connectionHandler.shutdown();
    }

    private void injectField(String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = BoeConnectionHandler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(connectionHandler, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to inject field '" + fieldName + "': " + e.getMessage());
        }
    }

    private Object getField(String fieldName) {
        try {
            java.lang.reflect.Field field = BoeConnectionHandler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(connectionHandler);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access field '" + fieldName + "': " + e.getMessage());
            return null;
        }
    }

    @Test
    void constructor_shouldSetHostAndPort() {
        BoeConnectionHandler handler = new BoeConnectionHandler("testHost", 8080);

        assertEquals("testHost", getFieldFromHandler(handler, "host"));
        assertEquals(8080, getFieldFromHandler(handler, "port"));
    }

    private Object getFieldFromHandler(BoeConnectionHandler handler, String fieldName) {
        try {
            java.lang.reflect.Field field = BoeConnectionHandler.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.get(handler);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to access field '" + fieldName + "': " + e.getMessage());
            return null;
        }
    }

    @Test
    void connect_shouldLogWarningIfAlreadyConnected() throws Exception {
        assertTrue(connectionHandler.isConnected());

        connectionHandler.connect().join();

        assertTrue(connectionHandler.isConnected());
    }

    @Test
    void disconnect_shouldCloseStreamsAndSocketSuccessfully() throws Exception {
        connectionHandler.disconnect().join();

        verify(mockInputStream).close();
        verify(mockOutputStream).close();
        verify(mockSocket).close();

        when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(connectionHandler.isConnected());
    }

    @Test
    void disconnect_shouldHandleIOExceptionDuringClose() throws Exception {
        doThrow(new IOException("Input stream close error")).when(mockInputStream).close();
        doThrow(new IOException("Output stream close error")).when(mockOutputStream).close();
        doThrow(new IOException("Socket close error")).when(mockSocket).close();

        assertDoesNotThrow(() -> connectionHandler.disconnect().join());

        verify(mockInputStream).close();
        verify(mockOutputStream).close();
        verify(mockSocket).close();
    }

    @Test
    void sendMessage_payload_shouldWriteToOutputStream() throws Exception {
        byte[] payload = {0x05, 0x06, 0x07};

        connectionHandler.sendMessage(payload).join();

        verify(mockOutputStream).write(any(byte[].class));
        verify(mockOutputStream).flush();
    }

    @Test
    void sendMessage_shouldThrowExceptionIfNotConnected() throws Exception {
        connectionHandler.disconnect().join();
        when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(connectionHandler.isConnected());

        byte[] payload = {0x01, 0x02};

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> connectionHandler.sendMessage(payload).join());
        assertTrue(thrown.getCause().getMessage().contains("Not connected"));
    }

    @Test
    void sendMessage_shouldThrowRuntimeExceptionOnWriteError() throws Exception {
        byte[] payload = {0x01, 0x02, 0x03};
        doThrow(new IOException("Write error")).when(mockOutputStream).write(any(byte[].class));

        CompletionException thrown = assertThrows(CompletionException.class,
                () -> connectionHandler.sendMessage(payload).join());
        assertTrue(thrown.getCause() instanceof RuntimeException);
    }

    @Test
    void startListener_shouldLogWarningIfNotConnected() throws Exception {
        connectionHandler.disconnect().join();
        when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(connectionHandler.isConnected());

        connectionHandler.startListener().join();
    }

    @Test
    void startListener_shouldProcessMessagesUntilStopped() throws Exception {

        final byte START_OF_MESSAGE_1 = (byte) 0xBA;
        final byte START_OF_MESSAGE_2 = (byte) 0xBA;

        byte[] msg1Data = new byte[]{
                START_OF_MESSAGE_1, START_OF_MESSAGE_2,
                0x08, 0x00,
                0x01,
                0x00,
                0x00, 0x00, 0x00, 0x00
        };

        byte[] msg2Data = new byte[]{
                START_OF_MESSAGE_1, START_OF_MESSAGE_2,
                0x08, 0x00,
                0x02,
                0x00,
                0x00, 0x00, 0x00, 0x00
        };

        ByteArrayOutputStream combinedStream = new ByteArrayOutputStream();
        combinedStream.write(msg1Data);
        combinedStream.write(msg2Data);

        InputStream testInputStream = new ByteArrayInputStream(combinedStream.toByteArray());

        BoeConnectionHandler spyHandler = spy(connectionHandler);
        doNothing().when(spyHandler).processMessage(any(BoeMessage.class));

        try {
            java.lang.reflect.Field field = BoeConnectionHandler.class.getDeclaredField("inputStream");
            field.setAccessible(true);
            field.set(spyHandler, testInputStream);
        } catch (Exception e) {
            fail("Failed to inject input stream: " + e.getMessage());
        }

        CompletableFuture<Void> listenerFuture = spyHandler.startListener();

        TimeUnit.MILLISECONDS.sleep(200);

        spyHandler.stopListener();

        try {
            listenerFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        }

        verify(spyHandler, times(2)).processMessage(any(BoeMessage.class));
    }

    @Test
    void stopListener_shouldSetRunningToFalse() throws Exception {

        InputStream blockingStream = new InputStream() {
            @Override
            public int read() throws IOException {
                try {
                    Thread.sleep(100);
                    return -1; // EOF
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
        };

        injectField("inputStream", blockingStream);

        CompletableFuture<Void> listenerFuture = connectionHandler.startListener();
        TimeUnit.MILLISECONDS.sleep(50);

        connectionHandler.stopListener();

        assertFalse((boolean) getField("running"));

        try {
            listenerFuture.get(1, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
        }
    }

    @Test
    void isConnected_shouldReturnTrueWhenConnected() {
        assertTrue(connectionHandler.isConnected());
    }

    @Test
    void isConnected_shouldReturnFalseWhenDisconnected() throws Exception {
        connectionHandler.disconnect().join();
        when(mockSocket.isClosed()).thenReturn(true);
        assertFalse(connectionHandler.isConnected());
    }

    @Test
    void isConnected_shouldReturnFalseWhenSocketIsNull() {
        injectField("socket", null);
        assertFalse(connectionHandler.isConnected());
    }

    @Test
    void shutdown_shouldShutdownExecutorService() {
        assertDoesNotThrow(() -> connectionHandler.shutdown());
    }
}