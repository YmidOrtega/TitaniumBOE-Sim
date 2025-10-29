package com.boe.simulator.client.config;

import java.util.logging.Level;

public class BoeClientConfiguration {

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final String sessionSubID;
    private final int connectionTimeout;
    private final int readTimeout;
    private final long heartbeatIntervalSeconds;
    private final boolean autoHeartbeat;
    private final boolean autoReconnect;
    private final int maxReconnectAttempts;
    private final long reconnectDelaySeconds;
    private final Level logLevel;

    private BoeClientConfiguration(Builder builder) {
        this.host = builder.host;
        this.port = builder.port;
        this.username = builder.username;
        this.password = builder.password;
        this.sessionSubID = builder.sessionSubID;
        this.connectionTimeout = builder.connectionTimeout;
        this.readTimeout = builder.readTimeout;
        this.heartbeatIntervalSeconds = builder.heartbeatIntervalSeconds;
        this.autoHeartbeat = builder.autoHeartbeat;
        this.autoReconnect = builder.autoReconnect;
        this.maxReconnectAttempts = builder.maxReconnectAttempts;
        this.reconnectDelaySeconds = builder.reconnectDelaySeconds;
        this.logLevel = builder.logLevel;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Getters
    public String getHost() { return host; }
    public int getPort() { return port; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }
    public String getSessionSubID() { return sessionSubID; }
    public int getConnectionTimeout() { return connectionTimeout; }
    public int getReadTimeout() { return readTimeout; }
    public long getHeartbeatIntervalSeconds() { return heartbeatIntervalSeconds; }
    public boolean isAutoHeartbeat() { return autoHeartbeat; }
    public boolean isAutoReconnect() { return autoReconnect; }
    public int getMaxReconnectAttempts() { return maxReconnectAttempts; }
    public long getReconnectDelaySeconds() { return reconnectDelaySeconds; }
    public Level getLogLevel() { return logLevel; }

    @Override
    public String toString() {
        return "BoeClientConfiguration{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", username='" + username + '\'' +
                ", sessionSubID='" + sessionSubID + '\'' +
                ", autoHeartbeat=" + autoHeartbeat +
                ", autoReconnect=" + autoReconnect +
                '}';
    }

    public static class Builder {
        private String host = "localhost";
        private int port = 8080;
        private String username;
        private String password;
        private String sessionSubID = "";
        private int connectionTimeout = 30000;  // 30 seconds
        private int readTimeout = 30000;        // 30 seconds
        private long heartbeatIntervalSeconds = 10;
        private boolean autoHeartbeat = true;
        private boolean autoReconnect = false;
        private int maxReconnectAttempts = 3;
        private long reconnectDelaySeconds = 5;
        private Level logLevel = Level.INFO;

        public Builder host(String host) {
            this.host = host;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder credentials(String username, String password) {
            this.username = username;
            this.password = password;
            return this;
        }

        public Builder username(String username) {
            this.username = username;
            return this;
        }

        public Builder password(String password) {
            this.password = password;
            return this;
        }

        public Builder sessionSubID(String sessionSubID) {
            this.sessionSubID = sessionSubID;
            return this;
        }

        public Builder connectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
            return this;
        }

        public Builder readTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder heartbeatInterval(long seconds) {
            this.heartbeatIntervalSeconds = seconds;
            return this;
        }

        public Builder autoHeartbeat(boolean autoHeartbeat) {
            this.autoHeartbeat = autoHeartbeat;
            return this;
        }

        public Builder autoReconnect(boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        public Builder maxReconnectAttempts(int maxReconnectAttempts) {
            this.maxReconnectAttempts = maxReconnectAttempts;
            return this;
        }

        public Builder reconnectDelay(long seconds) {
            this.reconnectDelaySeconds = seconds;
            return this;
        }

        public Builder logLevel(Level logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public BoeClientConfiguration build() {
            if (username == null || username.isEmpty()) throw new IllegalArgumentException("Username is required");
            if (password == null || password.isEmpty()) throw new IllegalArgumentException("Password is required");

            return new BoeClientConfiguration(this);
        }
    }
}
