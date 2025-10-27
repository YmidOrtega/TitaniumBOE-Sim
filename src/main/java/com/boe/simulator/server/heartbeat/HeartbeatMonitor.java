package com.boe.simulator.server.heartbeat;

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
    
    /**
     * Start heartbeat monitoring
     */
    public void start() {
        if (active) {
            LOGGER.warning("[Session " + handler.getSession().getConnectionId() + "] Heartbeat already active");
            return;
        }
        
        active = true;
        
        // Task 1: Send ServerHeartbeat periodically
        long sendInterval = config.getHeartbeatIntervalSeconds();
        sendTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeat,
            sendInterval,  // Initial delay
            sendInterval,  // Period
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
        
        LOGGER.info("[Session " + handler.getSession().getConnectionId() + 
                   "] Heartbeat monitor started (send every " + sendInterval + "s, timeout " + 
                   config.getHeartbeatTimeoutSeconds() + "s)");
    }
    
    /**
     * Send ServerHeartbeat to client
     */
    private void sendHeartbeat() {
        if (!active) return;
        
        try {
            ServerHeartbeatMessage heartbeat = new ServerHeartbeatMessage();
            heartbeat.setMatchingUnit(handler.getSession().getMatchingUnit());
            heartbeat.setSequenceNumber(handler.getSession().getNextSentSequenceNumber());
            
            byte[] heartbeatBytes = heartbeat.toBytes();
            handler.sendMessage(heartbeatBytes);
            
            handler.getSession().updateHeartbeatSent();
            
            LOGGER.fine("[Session " + handler.getSession().getConnectionId() + 
                       "] → Sent ServerHeartbeat (seq=" + heartbeat.getSequenceNumber() + ")");
            
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "[Session " + handler.getSession().getConnectionId() + 
                      "] Error sending heartbeat", e);
            stop();
        }
    }
    
    /**
     * Check if client heartbeat has timed out
     */
    private void checkTimeout() {
        if (!active) return;
        
        Instant lastReceived = handler.getSession().getLastHeartbeatReceived();
        
        // If never received a heartbeat yet, don't check (client might be just connecting)
        if (lastReceived == null) {
            return;
        }
        
        Duration timeSinceLastHeartbeat = Duration.between(lastReceived, Instant.now());
        long timeoutSeconds = config.getHeartbeatTimeoutSeconds();
        
        if (timeSinceLastHeartbeat.getSeconds() > timeoutSeconds) {
            LOGGER.warning("[Session " + handler.getSession().getConnectionId() + 
                          "] Client heartbeat timeout! Last received " + 
                          timeSinceLastHeartbeat.getSeconds() + "s ago (timeout=" + timeoutSeconds + "s)");
            
            // Disconnect client
            handler.stop();
            stop();
        }
    }
    
    /**
     * Stop heartbeat monitoring
     */
    public void stop() {
        if (!active) return;
        
        active = false;
        
        if (sendTask != null) {
            sendTask.cancel(false);
        }
        
        if (checkTask != null) {
            checkTask.cancel(false);
        }
        
        LOGGER.info("[Session " + handler.getSession().getConnectionId() + "] Heartbeat monitor stopped");
    }
    
    /**
     * Shutdown scheduler
     */
    public void shutdown() {
        stop();
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            scheduler.shutdownNow();
        }
    }
    
    public boolean isActive() {
        return active;
    }
}
