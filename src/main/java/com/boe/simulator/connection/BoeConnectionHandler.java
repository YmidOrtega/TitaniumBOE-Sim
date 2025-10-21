package com.boe.simulator.connection;

import com.boe.simulator.protocol.message.BoeMessage;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;

import java.net.Socket;
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
    
    private final String host;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final BoeMessageSerializer serializer = new BoeMessageSerializer();
    
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

    public CompletableFuture<Void> sendMessage(BoeMessage message) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try {
                    if (outputStream == null || socket == null || socket.isClosed()) throw new IOException("Not connected. Call connect() first.");
                    
                    byte[] data = serializer.serialize(message);
                    outputStream.write(data);
                    outputStream.flush();
                    LOGGER.fine("Message sent, length: " + message.getMessageLength());
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error sending message", e);
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

    public CompletableFuture<Void> sendMessage(byte[] payload) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try {
                    if (outputStream == null || socket == null || socket.isClosed()) throw new IOException("Not connected. Call connect() first.");
                    
                    byte[] data = serializer.serialize(payload);
                    outputStream.write(data);
                    outputStream.flush();
                    LOGGER.fine("Message sent, length: " + data.length);
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
                    BoeMessage message;
                    synchronized (this) {
                        if (!running || socket == null || socket.isClosed()) break;
                        message = serializer.deserialize(inputStream);
                    }
                    
                    LOGGER.info("Received message: " + message);
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

    public void stopListener() {
        synchronized (this) {
            running = false;
        }
    }

    public boolean isConnected() {
        synchronized (this) {
            return socket != null && !socket.isClosed();
        }
    }

    protected void processMessage(BoeMessage message) {
        LOGGER.info("Processing message: " + message);
    }

    public void shutdown() {
        executor.shutdown();
    }
}