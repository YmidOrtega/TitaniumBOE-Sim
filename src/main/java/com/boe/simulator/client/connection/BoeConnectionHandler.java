package com.boe.simulator.client.connection;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.client.listener.TradingMessageListener;
import com.boe.simulator.protocol.message.*;
import com.boe.simulator.protocol.serialization.BoeMessageSerializer;

public class BoeConnectionHandler {
    private static final Logger LOGGER = Logger.getLogger(BoeConnectionHandler.class.getName());

    private final String host;
    private final int port;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    private final BoeMessageSerializer serializer = new BoeMessageSerializer();
    private final List<TradingMessageListener> tradingListeners = new CopyOnWriteArrayList<>();

    private final Object readLock = new Object();
    private final Object writeLock = new Object();
    private final Object connectionLock = new Object();

    private Socket socket;
    private InputStream inputStream;
    private OutputStream outputStream;
    private volatile boolean running = false;

    private BoeMessageListener messageListener;

    public BoeConnectionHandler(String host, int port) {
        this.host = host;
        this.port = port;
    }

    public void addTradingListener(TradingMessageListener listener) {
        if (listener != null && !tradingListeners.contains(listener)) {
            tradingListeners.add(listener);
            LOGGER.log(Level.FINE, "Trading listener added: {0}", listener.getClass().getSimpleName());
        }
    }

    public void removeTradingListener(TradingMessageListener listener) {
        if (tradingListeners.remove(listener)) LOGGER.log(Level.FINE, "Trading listener removed: {0}", listener.getClass().getSimpleName());
    }

    public CompletableFuture<Void> connect() {
        return CompletableFuture.runAsync(() -> {
            synchronized (connectionLock) {
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
            synchronized (connectionLock) {
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
            synchronized (writeLock) {
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
            synchronized (writeLock) {
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
        try {
            synchronized (writeLock) {
                if (outputStream == null || socket == null || socket.isClosed()) throw new IOException("Not connected. Call connect() first.");

                LOGGER.log(Level.INFO, "Sending raw message: {0} bytes", messageBytes.length);
                LOGGER.log(Level.INFO, "First 10 bytes: {0}", bytesToHex(messageBytes, Math.min(10, messageBytes.length)));

                outputStream.write(messageBytes);
                outputStream.flush();

                LOGGER.info("Raw message sent and flushed successfully");
            }

            return CompletableFuture.completedFuture(null);

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error sending raw message", e);
            CompletableFuture<Void> future = new CompletableFuture<>();
            future.completeExceptionally(e);
            return future;
        }
    }

    private String bytesToHex(byte[] bytes, int length) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < length; i++) sb.append(String.format("%02X ", bytes[i]));
        return sb.toString().trim();
    }

    public CompletableFuture<Void> startListener() {
        return CompletableFuture.runAsync(() -> {
            synchronized (connectionLock) {
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
                    synchronized (readLock) {
                        if (!running || socket == null || socket.isClosed()) break;
                        message = serializer.deserialize(inputStream);
                    }

                    LOGGER.log(Level.INFO, "Received message: {0}", message);
                    processMessage(message);

                } catch (IOException e) {
                    if (!running) {
                        LOGGER.info("Listener stopped.");
                    } else {
                        // Check if this is a graceful close after authentication failure
                        String errorMsg = e.getMessage();
                        if (errorMsg != null && errorMsg.contains("Invalid start of message marker")) LOGGER.fine("Connection closed by server (possibly after authentication failure)");
                        else if (socket.isClosed() || !socket.isConnected()) LOGGER.fine("Socket closed");
                        else LOGGER.log(Level.WARNING, "Error reading message: {0}", e.getMessage());
                    }
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
        running = false;
    }

    public boolean isConnected() {
        synchronized (connectionLock) {
            return socket != null && !socket.isClosed();
        }
    }

    protected void processMessage(BoeMessage message) {
        byte messageType = message.getMessageType();
        String messageTypeName = BoeMessageFactory.getMessageTypeName(messageType);

        LOGGER.log(Level.INFO, "Processing {0}, length: {1}", new Object[]{messageTypeName, message.getLength()});

        try {
            Object specificMessage = BoeMessageFactory.createMessage(message);

            switch (specificMessage) {
                case null -> {
                    LOGGER.log(Level.WARNING, "Could not deserialize message type: {0}", messageTypeName);
                    if (messageListener != null) messageListener.onUnknownMessage(message);
                }
                case ServerHeartbeatMessage heartbeat -> processServerHeartbeat(heartbeat);
                case LoginResponseMessage login -> processLoginResponse(login);
                case LogoutResponseMessage logout -> processLogoutResponse(logout);

                // ✅ TRADING MESSAGES - Notify trading listeners
                case OrderAcknowledgmentMessage ack -> {
                    processOrderAcknowledgment(ack, message);
                    notifyTradingListeners(ack);  // ← AGREGAR
                }
                case OrderExecutedMessage exec -> {
                    processOrderExecuted(exec, message);  // ← AGREGAR
                    notifyTradingListeners(exec);  // ← AGREGAR
                }
                case OrderRejectedMessage rejected -> {
                    processOrderRejected(rejected, message);
                    notifyTradingListeners(rejected);  // ← AGREGAR
                }
                case OrderCancelledMessage cancelled -> {
                    processOrderCancelled(cancelled, message);  // ← AGREGAR
                    notifyTradingListeners(cancelled);  // ← AGREGAR
                }

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

    private void notifyTradingListeners(Object message) {
        if (tradingListeners.isEmpty()) return;

        switch (message) {
            case OrderAcknowledgmentMessage ack -> {
                for (TradingMessageListener listener : tradingListeners) {
                    try {
                        listener.onOrderAcknowledgment(ack);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error notifying listener about ack", e);
                    }
                }
            }
            case OrderExecutedMessage exec -> {
                for (TradingMessageListener listener : tradingListeners) {
                    try {
                        listener.onOrderExecuted(exec);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error notifying listener about execution", e);
                    }
                }
            }
            case OrderRejectedMessage rej -> {
                for (TradingMessageListener listener : tradingListeners) {
                    try {
                        listener.onOrderRejected(rej);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error notifying listener about rejection", e);
                    }
                }
            }
            case OrderCancelledMessage canc -> {
                for (TradingMessageListener listener : tradingListeners) {
                    try {
                        listener.onOrderCancelled(canc);
                    } catch (Exception e) {
                        LOGGER.log(Level.WARNING, "Error notifying listener about cancellation", e);
                    }
                }
            }
            default -> {
                // Not a trading message, ignore
            }
        }
    }

    private void processOrderExecuted(OrderExecutedMessage exec, BoeMessage originalMessage) {
        LOGGER.log(Level.INFO, "Order executed: ClOrdID={0}, ExecID={1}, Qty={2}, Price={3}",
                new Object[]{
                        exec.getClOrdID(),
                        exec.getExecID(),
                        exec.getLastPx(),
                        exec.getLastPx()
                });
        if (messageListener != null) {
            messageListener.onUnknownMessage(originalMessage);
        }
    }

    private void processOrderCancelled(OrderCancelledMessage cancelled, BoeMessage originalMessage) {
        LOGGER.log(Level.INFO, "Order cancelled: ClOrdID={0}, Reason={1}",
                new Object[]{
                        cancelled.getClOrdID(),
                        (char)cancelled.getCancelReason()
                });
        if (messageListener != null) {
            messageListener.onUnknownMessage(originalMessage);
        }
    }

    private void processOrderAcknowledgment(OrderAcknowledgmentMessage ack, BoeMessage originalMessage) {
        LOGGER.log(Level.INFO, "Order acknowledged: ClOrdID={0}, OrderID={1}",
                new Object[]{ack.getClOrdID(), ack.getOrderID()});
    }

    private void processOrderRejected(OrderRejectedMessage rejected, BoeMessage originalMessage) {
        LOGGER.log(Level.WARNING, "Order rejected: ClOrdID={0}, Reason={1}",
                new Object[]{rejected.getClOrdID(), (char)rejected.getOrderRejectReason()});
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
