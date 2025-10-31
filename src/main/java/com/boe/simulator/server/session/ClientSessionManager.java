package com.boe.simulator.server.session;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.persistence.model.PersistedSession;
import com.boe.simulator.server.persistence.repository.SessionRepository;

public class ClientSessionManager {
    private static final Logger LOGGER = Logger.getLogger(ClientSessionManager.class.getName());

    private final ConcurrentHashMap<Integer, ClientConnectionHandler> handlers;
    private final ConcurrentHashMap<String, ClientConnectionHandler> handlersByUsername;
    private final SessionStatistics statistics;

    private final SessionRepository sessionRepository;
    private final ConcurrentHashMap<Integer, PersistedSession> activeSessions;
    
    public ClientSessionManager(SessionRepository sessionRepository) {
        this.handlers = new ConcurrentHashMap<>();
        this.handlersByUsername = new ConcurrentHashMap<>();
        this.statistics = new SessionStatistics();
        this.sessionRepository = sessionRepository;
        this.activeSessions = new ConcurrentHashMap<>();
        
        LOGGER.info("ClientSessionManager initialized");
    }

    public ClientSessionManager() {
        this(null);
        LOGGER.warning("ClientSessionManager initialized WITHOUT persistence");
    }

    public void registerHandler(ClientConnectionHandler handler) {
        int connectionId = handler.getSession().getConnectionId();
        handlers.put(connectionId, handler);
        
        LOGGER.log(Level.INFO, "Registered handler for connection {0} (Total active: {1})", new Object[]{connectionId, handlers.size()});
        
        statistics.incrementTotalConnections();

        if (sessionRepository != null) {
            ClientSession session = handler.getSession();
            PersistedSession persistedSession = PersistedSession.createOnLogin(
                connectionId,
                session.getUsername() != null ? session.getUsername() : "unknown",
                session.getSessionSubID() != null ? session.getSessionSubID() : "",
                session.getRemoteAddress(),
                session.getCreatedAt(),
                false,
                null
            );
            activeSessions.put(connectionId, persistedSession);
        }
    }

    public void registerUsername(ClientConnectionHandler handler, String username) {
        handlersByUsername.put(username, handler);
        
        LOGGER.log(Level.INFO, "Registered username ''{0}'' for connection {1}", new Object[]{username, handler.getSession().getConnectionId()});
        
        statistics.incrementSuccessfulLogins();

        if (sessionRepository != null) {
            int connectionId = handler.getSession().getConnectionId();
            PersistedSession oldSession = activeSessions.get(connectionId);
            
            if (oldSession != null) {
                PersistedSession updatedSession = PersistedSession.createOnLogin(
                    connectionId,
                    username,
                    handler.getSession().getSessionSubID(),
                    handler.getSession().getRemoteAddress(),
                    handler.getSession().getCreatedAt(),
                    true,
                    null
                );
                activeSessions.put(connectionId, updatedSession);
            }
        }
    }
    
    public void recordFailedLogin(int connectionId, String username, String reason) {
        statistics.incrementFailedLogins();
        
        if (sessionRepository != null) {
            PersistedSession oldSession = activeSessions.get(connectionId);
            
            if (oldSession != null) {
                PersistedSession failedSession = PersistedSession.createOnLogin(
                    connectionId,
                    username,
                    oldSession.sessionSubID(),
                    oldSession.remoteAddress(),
                    oldSession.startTime(),
                    false,
                    reason
                );
                activeSessions.put(connectionId, failedSession);
            }
        }
    }

    public void unregisterHandler(ClientConnectionHandler handler) {
        int connectionId = handler.getSession().getConnectionId();
        handlers.remove(connectionId);
        
        String username = handler.getSession().getUsername();
        if (username != null) handlersByUsername.remove(username);
        
        LOGGER.log(Level.INFO, "Unregistered connection {0} (Total active: {1})", new Object[]{connectionId, handlers.size()});
    
        persistAndCloseSession(handler, "Connection closed");
    }

    private void persistAndCloseSession(ClientConnectionHandler handler, String disconnectReason) {
        if (sessionRepository == null) return;
        
        try {
            int connectionId = handler.getSession().getConnectionId();
            PersistedSession activeSession = activeSessions.remove(connectionId);
            
            if (activeSession == null) {
                LOGGER.log(Level.WARNING, "No active session found for connection {0}", connectionId);
                return;
            }
            
            ClientSession session = handler.getSession();

            PersistedSession closedSession = activeSession.closeSession(
                Instant.now(),
                session.getMessagesReceived(),
                session.getMessagesSent(),
                0,
                0,
                disconnectReason
            );
            
            closedSession = closedSession.withMetadata("finalState", session.getState().toString());
            
            sessionRepository.save(closedSession);
            
            LOGGER.log(Level.INFO, "✓ Persisted session {0} (user={1}, duration={2}s)", new Object[]{closedSession.sessionId(), closedSession.username(), closedSession.durationSeconds()});
            
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to persist session for connection " + handler.getSession().getConnectionId(), e);
        }
    }

    public void disconnectAndPersist(ClientConnectionHandler handler, String reason) {
        persistAndCloseSession(handler, reason);
        handler.stop();
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
            } catch (IOException | IllegalStateException e) {
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
                } catch (IOException | IllegalStateException e) {
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

        if (sessionRepository != null) {
            LOGGER.log(Level.INFO, "Persisted sessions (DB): {0}", sessionRepository.count());
            LOGGER.log(Level.INFO, "Active sessions (memory): {0}", activeSessions.size());
        }

        LOGGER.info("=====================================");
    }

    public SessionRepository getSessionRepository() {
        return sessionRepository;
    }
    
    public boolean isPersistenceEnabled() {
        return sessionRepository != null;
    }
}