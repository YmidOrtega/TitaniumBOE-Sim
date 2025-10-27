package com.boe.simulator.server.heartbeat;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.connection.ClientConnectionHandler;
import com.boe.simulator.protocol.message.ServerHeartbeatMessage;
import com.boe.simulator.server.config.ServerConfiguration;

public class HeartbeatMonitor {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatMonitor.class.getName());

    private final ClientConnectionHandler handler;
    private final ServerConfiguration config;
    private final ScheduledExecutorService scheduler;

    private ScheduledFuture<?> sendTask;
    private ScheduledFuture<?> checkTask;
    private volatile boolean active;

    public HeartbeatMonitor(ClientConnectionHandler handler, ServerConfiguration config) {
        this.handler = handler;
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(2);
        this.active = false;
    }

    public void start() {
        if (active) {
            LOGGER.log(Level.WARNING, "[Session {0}] Heartbeat already active", handler.getSession().getConnectionId());
            return;
        }

        active = true;

        // Task 1: Send ServerHeartbeat periodically
        long sendInterval = config.getHeartbeatIntervalSeconds();
        sendTask = scheduler.scheduleAtFixedRate(
                this::sendHeartbeat,
                sendInterval,
                sendInterval,
                TimeUnit.SECONDS
        );

        // Task 2: Check for client heartbeat timeout
        long checkInterval = 5; // Check every 5 seconds
        checkTask = scheduler.scheduleAtFixedRate(
                this::checkTimeout,
                checkInterval,
                checkInterval,
                TimeUnit.SECONDS
        );

        LOGGER.log(Level.INFO, "[Session {0}] Heartbeat monitor started (send every {1}s, timeout {2}s)", new Object[]{handler.getSession().getConnectionId(), sendInterval, config.getHeartbeatTimeoutSeconds()});
    }

    private void sendHeartbeat() {
        if (!active) return;

        try {
            ServerHeartbeatMessage heartbeat = new ServerHeartbeatMessage();
            heartbeat.setMatchingUnit(handler.getSession().getMatchingUnit());
            heartbeat.setSequenceNumber(handler.getSession().getNextSentSequenceNumber());

            byte[] heartbeatBytes = heartbeat.toBytes();
            handler.sendMessage(heartbeatBytes);

            handler.getSession().updateHeartbeatSent();

            LOGGER.log(Level.FINE, "[Session {0}] \u2192 Sent ServerHeartbeat (seq={1})", new Object[]{handler.getSession().getConnectionId(), heartbeat.getSequenceNumber()});

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "[Session " + handler.getSession().getConnectionId() + "] Error sending heartbeat", e);
            stop();
        }
    }

    private void checkTimeout() {
        if (!active) return;
        Instant lastReceived = handler.getSession().getLastHeartbeatReceived();

        // If never received a heartbeat yet, don't check (client might be just connecting)
        if (lastReceived == null) return;


        Duration timeSinceLastHeartbeat = Duration.between(lastReceived, Instant.now());
        long timeoutSeconds = config.getHeartbeatTimeoutSeconds();

        if (timeSinceLastHeartbeat.getSeconds() > timeoutSeconds) {
            LOGGER.log(Level.WARNING, "[Session {0}] Client heartbeat timeout! Last received {1}s ago (timeout={2}s)", new Object[]{handler.getSession().getConnectionId(), timeSinceLastHeartbeat.getSeconds(), timeoutSeconds});

            // Disconnect client
            handler.stop();
            stop();
        }
    }

    public void stop() {
        if (!active) return;
        active = false;
        if (sendTask != null) sendTask.cancel(false);
        if (checkTask != null) checkTask.cancel(false);

        LOGGER.log(Level.INFO, "[Session {0}] Heartbeat monitor stopped", handler.getSession().getConnectionId());
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

    public boolean isActive() {
        return active;
    }
}
