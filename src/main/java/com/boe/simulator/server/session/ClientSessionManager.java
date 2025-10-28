package com.boe.simulator.server.session;

import com.boe.simulator.connection.ClientConnectionHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class ClientSessionManager {
    private static final Logger LOGGER = Logger.getLogger(ClientSessionManager.class.getName());

    private final ConcurrentHashMap<Integer, ClientConnectionHandler> handlers;
    private final ConcurrentHashMap<String, ClientConnectionHandler> handlersByUsername;

    private final SessionStatistics statistics;
    
    public ClientSessionManager() {
        this.handlers = new ConcurrentHashMap<>();
        this.handlersByUsername = new ConcurrentHashMap<>();
        this.statistics = new SessionStatistics();
        
        LOGGER.info("ClientSessionManager initialized");
    }

    public void registerHandler(ClientConnectionHandler handler) {
        int connectionId = handler.getSession().getConnectionId();
        handlers.put(connectionId, handler);
        
        LOGGER.log(Level.INFO, "Registered handler for connection {0} (Total active: {1})", new Object[]{connectionId, handlers.size()});
        
        statistics.incrementTotalConnections();
    }

    public void registerUsername(ClientConnectionHandler handler, String username) {
        handlersByUsername.put(username, handler);
        
        LOGGER.log(Level.INFO, "Registered username ''{0}'' for connection {1}", new Object[]{username, handler.getSession().getConnectionId()});
        
        statistics.incrementSuccessfulLogins();
    }

    public void unregisterHandler(ClientConnectionHandler handler) {
        int connectionId = handler.getSession().getConnectionId();
        handlers.remove(connectionId);
        
        String username = handler.getSession().getUsername();
        if (username != null) {
            handlersByUsername.remove(username);
        }
        
        LOGGER.log(Level.INFO, "Unregistered connection {0} (Total active: {1})", new Object[]{connectionId, handlers.size()});
    }

    public ClientConnectionHandler getHandler(int connectionId) {
        return handlers.get(connectionId);
    }

    public ClientConnectionHandler getHandlerByUsername(String username) {
        return handlersByUsername.get(username);
    }

    public List<ClientConnectionHandler> getAllHandlers() {
        return new ArrayList<>(handlers.values());
    }
    
    public List<ClientConnectionHandler> getAuthenticatedHandlers() {
        return handlers.values().stream()
            .filter(h -> h.getSession().isAuthenticated())
            .collect(Collectors.toList());
    }

    public void broadcastMessage(byte[] messageBytes) {
        List<ClientConnectionHandler> authenticated = getAuthenticatedHandlers();
        
        LOGGER.log(Level.INFO, "Broadcasting message to {0} authenticated sessions", authenticated.size());
        
        int successCount = 0;
        for (ClientConnectionHandler handler : authenticated) {
            try {
                handler.sendMessage(messageBytes);
                successCount++;
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Failed to broadcast to connection {0}: {1}", new Object[]{handler.getSession().getConnectionId(), e.getMessage()});
            }
        }
        
        LOGGER.log(Level.INFO, "Broadcast completed: {0}/{1} succeeded", new Object[]{successCount, authenticated.size()});
    }
    
    public void broadcastToUsers(byte[] messageBytes, List<String> usernames) {
        LOGGER.log(Level.INFO, "Broadcasting message to {0} specific users", usernames.size());
        
        int successCount = 0;
        for (String username : usernames) {
            ClientConnectionHandler handler = handlersByUsername.get(username);
            if (handler != null && handler.getSession().isAuthenticated()) {
                try {
                    handler.sendMessage(messageBytes);
                    successCount++;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to send to user {0}: {1}", new Object[]{username, e.getMessage()});
                }
            }
        }
        
        LOGGER.log(Level.INFO, "User broadcast completed: {0}/{1} succeeded", new Object[]{successCount, usernames.size()});
    }

    public boolean disconnectUser(String username) {
        ClientConnectionHandler handler = handlersByUsername.get(username);
        if (handler != null) {
            LOGGER.log(Level.INFO, "Forcing disconnect for user: {0}", username);
            handler.stop();
            return true;
        }
        return false;
    }

    public void disconnectAll() {
        LOGGER.log(Level.INFO, "Disconnecting all sessions ({0} active)", handlers.size());
        
        for (ClientConnectionHandler handler : handlers.values()) {
            try {
                handler.stop();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error disconnecting connection {0}: {1}", new Object[]{handler.getSession().getConnectionId(), e.getMessage()});
            }
        }
    }
    
    // Statistics methods
    public int getActiveSessionCount() {
        return handlers.size();
    }
    
    public int getAuthenticatedSessionCount() {
        return (int) handlers.values().stream()
            .filter(h -> h.getSession().isAuthenticated())
            .count();
    }
    
    public SessionStatistics getStatistics() {
        return statistics;
    }

    public List<SessionInfo> getSessionInfoList() {
        return handlers.values().stream()
            .map(h -> new SessionInfo(h.getSession()))
            .collect(Collectors.toList());
    }

    public void printSessionSummary() {
        LOGGER.info("========== Session Summary ==========");
        LOGGER.log(Level.INFO, "Active connections: {0}", getActiveSessionCount());
        LOGGER.log(Level.INFO, "Authenticated sessions: {0}", getAuthenticatedSessionCount());
        LOGGER.log(Level.INFO, "Total connections (lifetime): {0}", statistics.getTotalConnections());
        LOGGER.log(Level.INFO, "Successful logins: {0}", statistics.getSuccessfulLogins());
        LOGGER.log(Level.INFO, "Failed logins: {0}", statistics.getFailedLogins());
        LOGGER.info("=====================================");
    }
}