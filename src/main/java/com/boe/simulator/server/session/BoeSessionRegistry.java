package com.boe.simulator.server.session;

import java.util.concurrent.ConcurrentHashMap;

public final class BoeSessionRegistry {
    private final ConcurrentHashMap<String, BoeSessionState> byKey = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, BoeSessionState> latestByUsername = new ConcurrentHashMap<>();

    public BoeSessionState bind(String username, String sessionSubID) {
        String subID = sessionSubID != null ? sessionSubID : "";
        BoeSessionState state = byKey.computeIfAbsent(username + "\u0000" + subID, k -> new BoeSessionState(username, subID));
        latestByUsername.put(username, state);
        return state;
    }

    public BoeSessionState latestForUser(String username) {
        return username != null ? latestByUsername.get(username) : null;
    }

    public int size() {
        return byKey.size();
    }

    public void clear() {
        byKey.clear();
        latestByUsername.clear();
    }
}
