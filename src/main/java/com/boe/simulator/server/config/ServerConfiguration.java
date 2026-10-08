package com.boe.simulator.server.config;

import java.util.logging.Level;

public class ServerConfiguration {
    
    // Network settings
    private final String host;
    private final int port;
    private final int maxConnections;
    private final int connectionTimeout;
    
    // Heartbeat settings
    private final long heartbeatIntervalSeconds;
    private final long heartbeatTimeoutSeconds;

    // Rate limiting
    private final int rateLimitPerSecond;

    // Flow control
    private final int maxUnacknowledgedMessages;
    private final int resumeReadingBelow;
    private final int maxOpenOrdersPerSession;

    // Logging
    private final Level logLevel;

    private boolean marketSimulatorEnabled = true;
    
    private ServerConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.maxConnections = builder.maxConnections;
        this.connectionTimeout = builder.connectionTimeout;
        this.heartbeatIntervalSeconds = builder.heartbeatIntervalSeconds;
        this.heartbeatTimeoutSeconds = builder.heartbeatTimeoutSeconds;
        this.rateLimitPerSecond = builder.rateLimitPerSecond;
        this.maxUnacknowledgedMessages = builder.maxUnacknowledgedMessages;
        this.resumeReadingBelow = builder.resumeReadingBelow;
        this.maxOpenOrdersPerSession = builder.maxOpenOrdersPerSession;
        this.logLevel = builder.logLevel;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ServerConfiguration getDefault() {
        return builder().build();
    }

    public boolean isMarketSimulatorEnabled() {
        return marketSimulatorEnabled;
    }

    public void setMarketSimulatorEnabled(boolean enabled) {
        this.marketSimulatorEnabled = enabled;
    }
    
    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getMaxConnections() { return maxConnections; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public long getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public long getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
    public int getRateLimitPerSecond() { return rateLimitPerSecond; }
    public int getMaxUnacknowledgedMessages() { return maxUnacknowledgedMessages; }
    public int getResumeReadingBelow() { return resumeReadingBelow; }
    public int getMaxOpenOrdersPerSession() { return maxOpenOrdersPerSession; }
    public Level getLogLevel() { return logLevel; }
    
    @Override
    public String toString() {
        return "ServerConfiguration{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", maxConnections=" + maxConnections +
                ", connectionTimeout=" + connectionTimeout + "ms" +
                ", heartbeatInterval=" + heartbeatIntervalSeconds + "s" +
                ", heartbeatTimeout=" + heartbeatTimeoutSeconds + "s" +
                ", rateLimit=" + rateLimitPerSecond + "/s" +
                ", maxUnacknowledged=" + maxUnacknowledgedMessages +
                ", resumeReadingBelow=" + resumeReadingBelow +
                ", maxOpenOrdersPerSession=" + maxOpenOrdersPerSession +
                ", logLevel=" + logLevel +
                '}';
    }
    
    public static class Builder {
        private String host = "0.0.0.0";
        private int port = 8080;
        private int maxConnections = 100;
        private int connectionTimeout = 30000; // 30 seconds
        private long heartbeatIntervalSeconds = 1;
        private long heartbeatTimeoutSeconds = 5;
        private int rateLimitPerSecond = 1_000;
        private int maxUnacknowledgedMessages = 1_024;
        private int resumeReadingBelow = 960;
        private int maxOpenOrdersPerSession = 2_000; // spec: 200,000 per port
        private Level logLevel = Level.INFO;
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535");
            this.port = port;
            return this;
        }
        
        public Builder maxConnections(int maxConnections) {
            if (maxConnections < 1) throw new IllegalArgumentException("Max connections must be at least 1");
            this.maxConnections = maxConnections;
            return this;
        }
        
        public Builder connectionTimeout(int connectionTimeout) {
            if (connectionTimeout < 1000) throw new IllegalArgumentException("Connection timeout must be at least 1000ms");
            this.connectionTimeout = connectionTimeout;
            return this;
        }
        
        public Builder heartbeatIntervalSeconds(long seconds) {
            if (seconds < 1) throw new IllegalArgumentException("Heartbeat interval must be at least 1 second");
            this.heartbeatIntervalSeconds = seconds;
            return this;
        }
        
        public Builder heartbeatTimeoutSeconds(long seconds) {
            if (seconds < 5) throw new IllegalArgumentException("Heartbeat timeout must be at least 5 seconds");
            this.heartbeatTimeoutSeconds = seconds;
            return this;
        }

        public Builder rateLimitPerSecond(int limit) {
            if (limit < 1) throw new IllegalArgumentException("Rate limit must be at least 1");
            this.rateLimitPerSecond = limit;
            return this;
        }

        public Builder flowControl(int maxUnacknowledged, int resumeBelow) {
            if (resumeBelow < 1 || resumeBelow > maxUnacknowledged)
                throw new IllegalArgumentException("Resume threshold must be between 1 and the pause threshold");
            this.maxUnacknowledgedMessages = maxUnacknowledged;
            this.resumeReadingBelow = resumeBelow;
            return this;
        }

        public Builder maxOpenOrdersPerSession(int limit) {
            if (limit < 1) throw new IllegalArgumentException("Max open orders must be at least 1");
            this.maxOpenOrdersPerSession = limit;
            return this;
        }
        
        public Builder logLevel(Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }
        
        public ServerConfiguration build() {
            return new ServerConfiguration(this);
        }
    }
}