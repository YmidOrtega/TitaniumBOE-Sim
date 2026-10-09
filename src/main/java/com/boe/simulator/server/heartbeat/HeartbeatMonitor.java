package com.boe.simulator.server.heartbeat;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.boe.simulator.protocol.message.ServerHeartbeatMessage;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.session.ClientSession;

public class HeartbeatMonitor {
    private static final Logger LOGGER = Logger.getLogger(HeartbeatMonitor.class.getName());

    // Shared across all sessions — avoids 2 platform threads per connection
    private static final ScheduledExecutorService SHARED_SCHEDULER =
            Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors());

    private static final long TICK_MILLIS = 200;

    private final ClientConnectionHandler handler;
    private final ServerConfiguration config;

    private ScheduledFuture<?> tickTask;
    private volatile boolean active;

    public HeartbeatMonitor(ClientConnectionHandler handler, ServerConfiguration config) {
        this.handler = handler;
        this.config = config;
        this.active = false;
    }

    public void start() {
        if (active) {
            LOGGER.log(Level.WARNING, "[Session {0}] Heartbeat already active", handler.getSession().getConnectionId());
            return;
        }

        active = true;
        handler.getSession().markInbound();

        tickTask = SHARED_SCHEDULER.scheduleAtFixedRate(this::tick, TICK_MILLIS, TICK_MILLIS, TimeUnit.MILLISECONDS);

        LOGGER.log(Level.INFO, "[Session {0}] Heartbeat monitor started (send after {1}s idle, timeout {2}s)", new Object[]{handler.getSession().getConnectionId(), config.getHeartbeatIntervalSeconds(), config.getHeartbeatTimeoutSeconds()});
    }

    private void tick() {
        if (!active) return;

        ClientSession session = handler.getSession();
        long timeoutNanos = TimeUnit.SECONDS.toNanos(config.getHeartbeatTimeoutSeconds());
        if (session.nanosSinceInbound() >= timeoutNanos) {
            LOGGER.log(Level.WARNING, "[Session {0}] No inbound data for {1}s - logging out",
                    new Object[]{session.getConnectionId(), config.getHeartbeatTimeoutSeconds()});
            stop();
            handler.logoutForHeartbeatTimeout();
            return;
        }

        if (session.nanosSinceOutbound() >= TimeUnit.SECONDS.toNanos(config.getHeartbeatIntervalSeconds())) {
            sendHeartbeat();
        }
    }

    private void sendHeartbeat() {
        try {
            handler.sendMessage(new ServerHeartbeatMessage().toBytes());
            handler.getSession().updateHeartbeatSent();

            LOGGER.log(Level.FINE, "[Session {0}] → Sent ServerHeartbeat", handler.getSession().getConnectionId());

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, e, () -> "[Session " + handler.getSession().getConnectionId() + "] Error sending heartbeat");
            stop();
        }
    }

    public void stop() {
        if (!active) return;

        active = false;

        if (tickTask != null) tickTask.cancel(false);
        LOGGER.log(Level.INFO, "[Session {0}] Heartbeat monitor stopped", handler.getSession().getConnectionId());
    }

    public void shutdown() {
        stop();
        // SHARED_SCHEDULER is intentionally not shut down here — it's application-scoped
    }

    public boolean isActive() {
        return active;
    }
}
