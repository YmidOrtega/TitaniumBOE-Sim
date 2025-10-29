package com.boe.simulator.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.BoeMessage;
import com.boe.simulator.protocol.message.BoeMessageFactory;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.ServerHeartbeatMessage;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;

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

    private BoeMessageListener messageListener;

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
                    LOGGER.log(Level.INFO, "Connected to {0}:{1}", new Object[]{host, port});
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

                LOGGER.log(Level.INFO, "Disconnected from {0}:{1}", new Object[]{host, port});
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
                    LOGGER.log(Level.FINE, "Message sent, length: {0}", message.getLength());
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
                    LOGGER.log(Level.FINE, "Message sent, length: {0}", data.length);
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error sending message", e);
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

    public CompletableFuture<Void> sendMessageRaw(byte[] messageBytes) {
        return CompletableFuture.runAsync(() -> {
            synchronized (this) {
                try {
                    if (outputStream == null || socket == null || socket.isClosed()) throw new IOException("Not connected. Call connect() first.");
                
                    LOGGER.log(Level.INFO, "Sending raw message: {0} bytes", messageBytes.length);
                    LOGGER.log(Level.INFO, "First 10 bytes: {0}", bytesToHex(messageBytes, Math.min(10, messageBytes.length)));

                    outputStream.write(messageBytes);
                    outputStream.flush();

                    LOGGER.info("Raw message sent and flushed successfully");
                } catch (IOException e) {
                    LOGGER.log(Level.SEVERE, "Error sending raw message", e);
                    throw new RuntimeException(e);
                }
            }
        }, executor);
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(String.format("%02X ", bytes[i]));
        return sb.toString().trim();
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

                    LOGGER.log(Level.INFO, "Received message: {0}", message);
                    processMessage(message);

                } catch (IOException e) {
                    if (!running) LOGGER.info("Listener stopped.");
                    else LOGGER.log(Level.SEVERE, "Error reading message", e);
                    break;
                }
            }

            try {
                disconnect().get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.log(Level.WARNING, "Disconnection interrupted", e);
            } catch (ExecutionException e) {
                LOGGER.log(Level.SEVERE, "Error during disconnect", e);
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
        byte messageType = message.getMessageType();
        String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

        LOGGER.log(Level.INFO, "Processing {0}, length: {1}", new Object[]{messageTypeName, message.getLength()});

        try {
            // Use factory to create specific message object
            Object specificMessage = BoeMessageFactory.createMessage(message);

            switch (specificMessage) {
                case null -> {
                    LOGGER.log(Level.WARNING, "Could not deserialize message type: {0}", messageTypeName);
                    if (messageListener != null) messageListener.onUnknownMessage(message);
                }
                case ServerHeartbeatMessage heartbeat -> processServerHeartbeat(heartbeat);
                case LoginResponseMessage login -> processLoginResponse(login);
                case LogoutResponseMessage logout -> processLogoutResponse(logout);
                default -> {
                    LOGGER.log(Level.WARNING, "Unhandled message type: {0}", specificMessage.getClass().getName());
                    if (messageListener != null) messageListener.onUnknownMessage(message);
                }
            }

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error processing " + messageTypeName, e);
            if (messageListener != null) messageListener.onMessageError(messageType, e);
            
        }
    }

    private void processServerHeartbeat(ServerHeartbeatMessage heartbeat) {
        LOGGER.log(Level.FINE, "Received server heartbeat: {0}", heartbeat);
        if (messageListener != null) messageListener.onServerHeartbeat(heartbeat);
        
    }

    private void processLoginResponse(LoginResponseMessage response) {
        LOGGER.log(Level.INFO, "Received login response: {0}", response);
        if (response.isAccepted()) LOGGER.log(Level.INFO, "Login accepted: {0}", response.getLoginResponseText());
        else if (response.isRejected()) LOGGER.log(Level.WARNING, "Login rejected: {0}", response.getLoginResponseText());
        if (messageListener != null) messageListener.onLoginResponse(response);

    }

    private void processLogoutResponse(LogoutResponseMessage response) {
        LOGGER.log(Level.INFO, "Received logout response: {0}", response);
        if (messageListener != null) messageListener.onLogoutResponse(response);
        
    }

    public void shutdown() {
        executor.shutdown();
    }

    public void setMessageListener(BoeMessageListener listener) {
        this.messageListener = listener;
    }

    public BoeMessageListener getMessageListener() {
        return messageListener;
    }
}