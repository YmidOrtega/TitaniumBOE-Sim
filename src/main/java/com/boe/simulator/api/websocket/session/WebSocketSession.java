package com.boe.simulator.api.websocket.session;

import io.javalin.websocket.WsContext;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class WebSocketSession {

    private final String sessionId;
    private final WsContext context;
    private final long connectedAt;
    private final String username;
    private final Set<String> subscribedSymbols;
    private volatile long lastActivity;

    public WebSocketSession(String sessionId, WsContext context, String username) {
        this.sessionId = sessionId;
        this.context = context;
        this.username = username;
        this.connectedAt = System.currentTimeMillis();
        this.lastActivity = System.currentTimeMillis();
        this.subscribedSymbols = ConcurrentHashMap.newKeySet();
    }

    public void subscribe(String symbol) {
        subscribedSymbols.add(symbol);
        updateActivity();
    }

    public void unsubscribe(String symbol) {
        subscribedSymbols.remove(symbol);
        updateActivity();
    }

    public boolean isSubscribedTo(String symbol) {
        return subscribedSymbols.contains(symbol);
    }

    public void updateActivity() {
        this.lastActivity = System.currentTimeMillis();
    }

    public boolean isActive() {
        // Consider inactive if no activity for 5 minutes
        return (System.currentTimeMillis() - lastActivity) < 300_000;
    }

    // Getters
    public String getSessionId() { return sessionId; }
    public WsContext getContext() { return context; }
    public long getConnectedAt() { return connectedAt; }
    public String getUsername() { return username; }
    public Set<String> getSubscribedSymbols() { return new HashSet<>(subscribedSymbols); }
    public long getLastActivity() { return lastActivity; }
}