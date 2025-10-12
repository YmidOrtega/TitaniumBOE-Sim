package com.boe.simulator.connection;

import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

public class BoeConnectionHandler {
    private static final Logger LOGGER = Logger.getLogger(BoeConnectionHandler.class.getName());
    private static final short MESSAGE_LENGTH_FIELD_SIZE = 4;
    
    private final String host;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    
    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running = false;

    public BoeConnectionHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try {
                    if (socket != null && !socket.isClosed()) {
                        LOGGER.warning("Already connected. Disconnect first.");
                        return;
                    }
                    socket = new Socket(host, port);
                    inputStream = socket.getInputStream();
                    outputStream = socket.getOutputStream();
                    LOGGER.info("Connected to " + host + ":" + port);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error connecting", e);
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

    public CompletableFuture<Void> disconnect() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                running = false;
                
                if (inputStream != null) {
                    try {
                        inputStream.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error closing input stream", e);
                    }
                }
                
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error closing output stream", e);
                    }
                }
                
                if (socket != null && !socket.isClosed()) {
                    try {
                        socket.close();
                    } catch (IOException e) {
                        LOGGER.log(Level.WARNING, "Error closing socket", e);
                    }
                }
                
                LOGGER.info("Disconnected from " + host + ":" + port);
            }
        }, executor);
    }

    private void readFully(byte[] buffer, int offset, int length) throws IOException {
        int bytesRead = 0;
        while (bytesRead < length) {
            int count = inputStream.read(buffer, offset + bytesRead, length - bytesRead);
            if (count < 0) throw new IOException("End of stream reached before reading fully.");

            bytesRead += count;
        }
    }

    private byte[] readMessage() throws IOException {
        byte[] lengthBytes = new byte[2];
        readFully(lengthBytes, 0, 2);
        
        ByteBuffer lengthBuffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = lengthBuffer.getShort() & 0xFFFF;
        
        if (messageLength <= 2) throw new IOException("Invalid message length: " + messageLength);
        
        byte[] message = new byte[messageLength];

        System.arraycopy(lengthBytes, 0, message, 0, 2); 

        int remainingLength = messageLength - 2;
        if (remainingLength > 0)  readFully(message, 2, remainingLength);
        
        return message;
    }

    public CompletableFuture<Void> sendMessage(byte[] message) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try {
                    if (outputStream == null || socket == null || socket.isClosed()) throw new IOException("Not connected. Call connect() first.");
                    
                    outputStream.write(message);
                    outputStream.flush();
                    LOGGER.fine("Message sent, length: " + message.length);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error sending message", e);
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

    public CompletableFuture<Void> startListener() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                if (socket == null || socket.isClosed()) {
                    LOGGER.severe("Not connected. Call connect() first.");
                    return;
                }
                running = true;
            }

            LOGGER.info("Starting listener...");

            while (running) {
                try {
                    byte[] message;
                    synchronized (this) {
                        if (!running || socket == null || socket.isClosed()) break;
                        
                        message = readMessage();
                    }
                    
                    LOGGER.info("Received message of length: " + message.length);
                    processMessage(message);

                } catch (IOException e) {
                    if (!running) LOGGER.info("Listener stopped.");
                    else LOGGER.log(Level.SEVERE, "Error reading message", e);
                    break;
                }
            }

            try {
                disconnect().get();
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error disconnecting", e);
            }
        }, executor);
    }

    public CompletableFuture<Void> stopListener() {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                running = false;
            }
        }, executor);
    }

    public boolean isConnected() {
        synchronized (this) {
            return socket != null && !socket.isClosed();
        }
    }

    protected void processMessage(byte[] message) {
        // Override this method to handle incoming messages
        LOGGER.info("Processing message of length: " + message.length);
    }

    public void shutdown() {
        executor.shutdown();
    }
}