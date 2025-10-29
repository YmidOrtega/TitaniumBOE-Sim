package com.boe.simulator.client.heartbeat;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.protocol.message.ClientHeartbeatMessage;

public class ClientHeartbeatManager {
    private static final Logger LOGGER = Logger.getLogger(ClientHeartbeatManager.class.getName());

    private final BoeConnectionHandler connectionHandler;
    private final long heartbeatIntervalSeconds;
    private final long serverHeartbeatTimeoutSeconds;

    private final ScheduledExecutorService scheduler;
    private final AtomicInteger sequenceNumber;

    private ScheduledFuture<?> sendTask;
    private ScheduledFuture<?> checkTask;
    private volatile boolean active;

    private volatile Instant lastServerHeartbeatReceived;
    private volatile Instant lastClientHeartbeatSent;

    private HeartbeatTimeoutListener timeoutListener;

    public ClientHeartbeatManager(BoeConnectionHandler connectionHandler, long heartbeatIntervalSeconds, long serverHeartbeatTimeoutSeconds) {
        this.connectionHandler = connectionHandler;
        this.heartbeatIntervalSeconds = heartbeatIntervalSeconds;
        this.serverHeartbeatTimeoutSeconds = serverHeartbeatTimeoutSeconds;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.sequenceNumber = new AtomicInteger(1);
        this.active = false;
    }

    public void start() {
        if (active) {
            LOGGER.warning("Heartbeat manager already active");
            return;
        }

        active = true;
        lastServerHeartbeatReceived = Instant.now();

        sendTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                heartbeatIntervalSeconds,
                heartbeatIntervalSeconds,
                TimeUnit.SECONDS
        );

        checkTask = scheduler.scheduleAtFixedRate(
                this::checkServerHeartbeatTimeout,
                5,  // Check every 5 seconds
                5,
                TimeUnit.SECONDS
        );

        LOGGER.log(Level.INFO, "Client heartbeat started (send every {0}s, timeout {1}s)", new Object[]{heartbeatIntervalSeconds, serverHeartbeatTimeoutSeconds});
    }

    public void stop() {
        if (!active) return;

        active = false;

        if (sendTask != null) sendTask.cancel(false);
        if (checkTask != null) checkTask.cancel(false);

        LOGGER.info("Client heartbeat stopped");
    }

    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) scheduler.shutdownNow();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }

    private void sendHeartbeat() {
        if (!active) return;

        try {
            ClientHeartbeatMessage heartbeat = new ClientHeartbeatMessage();
            heartbeat.setSequenceNumber(sequenceNumber.getAndIncrement());

            byte[] heartbeatBytes = heartbeat.toBytes();
            connectionHandler.sendMessageRaw(heartbeatBytes).get();

            lastClientHeartbeatSent = Instant.now();

            LOGGER.log(Level.FINE, "Client heartbeat sent (seq={0})", heartbeat.getSequenceNumber());

        } catch (InterruptedException | ExecutionException e) {
            LOGGER.log(Level.WARNING, "Failed to send heartbeat", e);
            Thread.currentThread().interrupt();
        }
    }

    private void checkServerHeartbeatTimeout() {
        if (!active || lastServerHeartbeatReceived == null) return;

        Duration timeSinceLastHeartbeat = Duration.between(lastServerHeartbeatReceived, Instant.now());

        if (timeSinceLastHeartbeat.getSeconds() > serverHeartbeatTimeoutSeconds) {
            LOGGER.log(Level.SEVERE, "Server heartbeat timeout! Last received {0}s ago", timeSinceLastHeartbeat.getSeconds());

            // Notify listener
            if (timeoutListener != null) timeoutListener.onHeartbeatTimeout();

            // Stop heartbeat
            stop();
        }
    }

    public void notifyServerHeartbeatReceived() {
        lastServerHeartbeatReceived = Instant.now();
        LOGGER.fine("Server heartbeat received - timeout reset");
    }

    public void setTimeoutListener(HeartbeatTimeoutListener listener) {
        this.timeoutListener = listener;
    }

    public Instant getLastServerHeartbeatReceived() {
        return lastServerHeartbeatReceived;
    }

    public Instant getLastClientHeartbeatSent() {
        return lastClientHeartbeatSent;
    }

    public boolean isActive() {
        return active;
    }

    public interface HeartbeatTimeoutListener {
        void onHeartbeatTimeout();
    }
}