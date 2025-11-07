package com.boe.simulator.server.order;

public class RestExecutionContext implements OrderExecutionContext {
    private final String username;

    public RestExecutionContext(String username) {
        this.username = username;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getSessionIdentifier() {
        return "REST-API";
    }

    @Override
    public boolean supportsNotifications() {
        return false; // REST no recibe notificaciones push
    }
}