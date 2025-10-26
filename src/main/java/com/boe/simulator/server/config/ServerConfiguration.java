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
    
    // Logging
    private final Level logLevel;
    
    private ServerConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.maxConnections = builder.maxConnections;
        this.connectionTimeout = builder.connectionTimeout;
        this.heartbeatIntervalSeconds = builder.heartbeatIntervalSeconds;
        this.heartbeatTimeoutSeconds = builder.heartbeatTimeoutSeconds;
        this.logLevel = builder.logLevel;
    }
    
    public static Builder builder() {
        return new Builder();
    }
    
    public static ServerConfiguration getDefault() {
        return builder().build();
    }
    
    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public int getMaxConnections() { return maxConnections; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public long getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public long getHeartbeatTimeoutSeconds() { return heartbeatTimeoutSeconds; }
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
                ", logLevel=" + logLevel +
                '}';
    }
    
    public static class Builder {
        private String host = "0.0.0.0";
        private int port = 8080;
        private int maxConnections = 100;
        private int connectionTimeout = 30000; // 30 seconds
        private long heartbeatIntervalSeconds = 10;
        private long heartbeatTimeoutSeconds = 30;
        private Level logLevel = Level.INFO;
        
        public Builder host(String host) {
            this.host = host;
            return this;
        }
        
        public Builder port(int port) {
            if (port < 1 || port > 65535) {
                throw new IllegalArgumentException("Port must be between 1 and 65535");
            }
            this.port = port;
            return this;
        }
        
        public Builder maxConnections(int maxConnections) {
            if (maxConnections < 1) {
                throw new IllegalArgumentException("Max connections must be at least 1");
            }
            this.maxConnections = maxConnections;
            return this;
        }
        
        public Builder connectionTimeout(int connectionTimeout) {
            if (connectionTimeout < 1000) {
                throw new IllegalArgumentException("Connection timeout must be at least 1000ms");
            }
            this.connectionTimeout = connectionTimeout;
            return this;
        }
        
        public Builder heartbeatIntervalSeconds(long seconds) {
            if (seconds < 1) {
                throw new IllegalArgumentException("Heartbeat interval must be at least 1 second");
            }
            this.heartbeatIntervalSeconds = seconds;
            return this;
        }
        
        public Builder heartbeatTimeoutSeconds(long seconds) {
            if (seconds < 5) {
                throw new IllegalArgumentException("Heartbeat timeout must be at least 5 seconds");
            }
            this.heartbeatTimeoutSeconds = seconds;
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