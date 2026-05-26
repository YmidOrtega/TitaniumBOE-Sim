package com.boe.simulator.server.order;

public record RestExecutionContext(String username) implements OrderExecutionContext {

    @Override
    public String getUsername() { return username; }

    @Override
    public String getSessionIdentifier() { return "REST-API"; }

    @Override
    public boolean supportsNotifications() { return false; }
}
