package com.boe.simulator.client.session;

public enum ClientSessionState {
    DISCONNECTED("Disconnected", "Not connected to server"),
    CONNECTING("Connecting", "Establishing connection"),
    CONNECTED("Connected", "TCP connection established"),
    AUTHENTICATING("Authenticating", "Sending login request"),
    AUTHENTICATED("Authenticated", "Login successful, session active"),
    ACTIVE("Active", "Fully operational with heartbeat"),
    DISCONNECTING("Disconnecting", "Closing connection"),
    ERROR("Error", "Error state"),
    RECONNECTING("Reconnecting", "Attempting to reconnect");

    private final String name;
    private final String description;

    ClientSessionState(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public boolean isConnected() {
        return this == CONNECTED || this == AUTHENTICATING || this == AUTHENTICATED || this == ACTIVE;
    }

    public boolean isAuthenticated() {
        return this == AUTHENTICATED || this == ACTIVE;
    }

    @Override
    public String toString() {
        return name;
    }
}