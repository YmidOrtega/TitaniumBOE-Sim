package com.boe.simulator.api.websocket;

import com.boe.simulator.api.websocket.dto.*;
import com.boe.simulator.api.websocket.session.WebSocketSession;
import com.boe.simulator.server.matching.OrderBook;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.order.Order;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.javalin.websocket.WsContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketService {
    private static final Logger LOGGER = Logger.getLogger(WebSocketService.class.getName());

    private static final long IDLE_TIMEOUT_MILLIS = 300_000; // 5 minutes
    private static final long CLEANUP_INTERVAL_SECONDS = 60;

    private final Map<String, WebSocketSession> sessions;
    private final ObjectMapper objectMapper;
    private ScheduledExecutorService cleanupScheduler;

    public WebSocketService() {
        this.sessions = new ConcurrentHashMap<>();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    // Session management
    public void addSession(String sessionId, WsContext ctx, String username) {
        WebSocketSession session = new WebSocketSession(sessionId, ctx, username);
        sessions.put(sessionId, session);

        LOGGER.log(Level.INFO, "WebSocket session added: {0} (user: {1}, total: {2})",
                new Object[]{sessionId, username, sessions.size()});
    }

    public void removeSession(String sessionId) {
        WebSocketSession removed = sessions.remove(sessionId);
        if (removed != null) {
            LOGGER.log(Level.INFO, "WebSocket session removed: {0} (total: {1})",
                    new Object[]{sessionId, sessions.size()});
        }
    }

    public void recordActivity(String sessionId) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) session.updateActivity();
    }

    public void subscribe(String sessionId, String symbol) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            session.subscribe(symbol);
            LOGGER.log(Level.FINE, "Session {0} subscribed to {1}",
                    new Object[]{sessionId, symbol});
        }
    }

    public void unsubscribe(String sessionId, String symbol) {
        WebSocketSession session = sessions.get(sessionId);
        if (session != null) {
            session.unsubscribe(symbol);
            LOGGER.log(Level.FINE, "Session {0} unsubscribed from {1}",
                    new Object[]{sessionId, symbol});
        }
    }

    // Broadcasting methods
    public void broadcastOrderBookUpdate(String symbol, OrderBook orderBook, int depth) {
        OrderBookUpdateMessage message = createOrderBookMessage(symbol, orderBook, depth);
        broadcastToSymbol(symbol, message);
    }

    public void broadcastTrade(Trade trade) {
        TradeUpdateMessage message = new TradeUpdateMessage(trade);
        broadcastToSymbol(trade.getSymbol(), message);
    }

    public void broadcastOrderStatus(Order order) {
        OrderStatusUpdateMessage message = new OrderStatusUpdateMessage(order);

        // Send to user's sessions if authenticated
        if (order.getUsername() != null) broadcastToUser(order.getUsername(), message);

        // Also send to all sessions subscribed to this symbol
        broadcastToSymbol(order.getSymbol(), message);
    }

    private void broadcastToSymbol(String symbol, WebSocketMessage message) {
        String json = serializeMessage(message);
        if (json == null) return;

        sessions.values().stream()
                .filter(session -> session.isSubscribedTo(symbol))
                .forEach(session -> sendToSession(session, json));
    }

    private void broadcastToUser(String username, WebSocketMessage message) {
        String json = serializeMessage(message);
        if (json == null) return;

        sessions.values().stream()
                .filter(session -> username.equals(session.getUsername()))
                .forEach(session -> sendToSession(session, json));
    }

    public void broadcast(WebSocketMessage message) {
        String json = serializeMessage(message);
        if (json == null) return;

        sessions.values().forEach(session -> sendToSession(session, json));
    }

    private void sendToSession(WebSocketSession session, String json) {
        try {
            WsContext ctx = session.getContext();
            if (ctx.session.isOpen()) {
                ctx.send(json);
                session.updateActivity();
            } else {
                // Session closed, remove it
                removeSession(session.getSessionId());
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to send message to session: " + session.getSessionId(), e);
            removeSession(session.getSessionId());
        }
    }

    private String serializeMessage(WebSocketMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to serialize WebSocket message", e);
            return null;
        }
    }

    private OrderBookUpdateMessage createOrderBookMessage(String symbol, OrderBook orderBook, int depth) {
        var snapshot = orderBook.getSnapshot(depth);

        List<OrderBookUpdateMessage.PriceLevel> bids = snapshot.bids().stream()
                .map(level -> new OrderBookUpdateMessage.PriceLevel(
                        level.price(),
                        level.quantity(),
                        level.orderCount()
                ))
                .toList();

        List<OrderBookUpdateMessage.PriceLevel> asks = snapshot.asks().stream()
                .map(level -> new OrderBookUpdateMessage.PriceLevel(
                        level.price(),
                        level.quantity(),
                        level.orderCount()
                ))
                .toList();

        return new OrderBookUpdateMessage(symbol, bids, asks, orderBook.getLastTradePrice());
    }

    // Stats
    public int getActiveSessionCount() {
        return sessions.size();
    }

    public List<String> getActiveSessions() {
        return new ArrayList<>(sessions.keySet());
    }

    public synchronized void startCleanup() {
        if (cleanupScheduler != null) return;
        cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "WebSocket-Cleanup");
            t.setDaemon(true);
            return t;
        });
        cleanupScheduler.scheduleAtFixedRate(() -> {
            try {
                cleanupInactiveSessions();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "WebSocket cleanup failed", e);
            }
        }, CLEANUP_INTERVAL_SECONDS, CLEANUP_INTERVAL_SECONDS, TimeUnit.SECONDS);
    }

    public synchronized void stopCleanup() {
        if (cleanupScheduler == null) return;
        cleanupScheduler.shutdownNow();
        cleanupScheduler = null;
    }

    public int cleanupInactiveSessions() {
        return cleanupInactiveSessions(IDLE_TIMEOUT_MILLIS);
    }

    int cleanupInactiveSessions(long idleMillis) {
        List<WebSocketSession> stale = sessions.values().stream()
                .filter(session -> !session.getContext().session.isOpen() || session.isIdleFor(idleMillis))
                .toList();

        for (WebSocketSession session : stale) {
            WsContext ctx = session.getContext();
            if (ctx.session.isOpen()) {
                try {
                    ctx.closeSession(1001, "Idle timeout");
                } catch (Exception e) {
                    LOGGER.log(Level.FINE, "Error closing idle WebSocket session " + session.getSessionId(), e);
                }
            }
            removeSession(session.getSessionId());
        }

        if (!stale.isEmpty()) LOGGER.log(Level.INFO, "Cleaned up {0} inactive WebSocket sessions", stale.size());
        return stale.size();
    }
}