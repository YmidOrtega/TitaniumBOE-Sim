package com.boe.simulator.client.interactive;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.interactive.notification.NotificationManager;
import com.boe.simulator.client.interactive.notification.NotificationTradingListener;
import com.boe.simulator.client.interactive.notification.OrderStatusPoller;

import java.util.HashMap;
import java.util.Map;

public class SessionContext {
    private BoeClient client;
    private String username;
    private String host;
    private int port;
    private boolean authenticated;
    private final Map<String, Object> sessionData;
    private long connectedAt;
    private final NotificationManager notificationManager;
    private OrderStatusPoller orderPoller;

    public SessionContext() {
        this.sessionData = new HashMap<>();
        this.authenticated = false;
        this.notificationManager = new NotificationManager();
    }

    public NotificationManager getNotificationManager() {
        return notificationManager;
    }

    // Connection
    public void connect(String host, int port, BoeClient client) {
        this.host = host;
        this.port = port;
        this.client = client;
        this.connectedAt = System.currentTimeMillis();

        if (orderPoller == null) orderPoller = new OrderStatusPoller(this, notificationManager);
    }

    public void disconnect() {

        // Remove trading listener
        Object listener = sessionData.get("tradingListener");
        if (listener instanceof NotificationTradingListener && client != null) client.getConnectionHandler().removeTradingListener((NotificationTradingListener) listener);

        // Stop notification manager
        if (notificationManager != null) notificationManager.stop();
        if (client != null) client.disconnect();

        this.client = null;
        this.authenticated = false;
    }

    public boolean isConnected() {
        return client != null && client.isConnected();
    }

    // Authentication
    public void authenticate(String username) {
        this.username = username;
        this.authenticated = true;

        if (orderPoller != null) orderPoller.start();
    }

    public void logout() {
        this.authenticated = false;
        this.username = null;
    }

    // Session data
    public void set(String key, Object value) {
        sessionData.put(key, value);
    }

    public Object get(String key) {
        return sessionData.get(key);
    }

    // Getters
    public BoeClient getClient() { return client; }
    public String getUsername() { return username; }
    public String getHost() { return host; }
    public int getPort() { return port; }
    public boolean isAuthenticated() { return authenticated; }
    public long getConnectedAt() { return connectedAt; }

    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - connectedAt) / 1000;
    }
}