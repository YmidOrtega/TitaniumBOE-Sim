package com.boe.simulator.api.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.websocket.WsConfig;
import io.javalin.websocket.WsContext;

import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketHandler {
    private static final Logger LOGGER = Logger.getLogger(WebSocketHandler.class.getName());

    private final WebSocketService webSocketService;
    private final ObjectMapper objectMapper;

    public WebSocketHandler(WebSocketService webSocketService) {
        this.webSocketService = webSocketService;
        this.objectMapper = new ObjectMapper();
    }

    public void configure(WsConfig ws) {
        ws.onConnect(ctx -> {
            String sessionId = UUID.randomUUID().toString();
            String username = ctx.queryParam("username");

            ctx.attribute("sessionId", sessionId);
            webSocketService.addSession(sessionId, ctx, username);

            ctx.send("""
                {
                    "type": "connected",
                    "sessionId": "%s",
                    "timestamp": %d
                }
                """.formatted(sessionId, System.currentTimeMillis()));

            LOGGER.log(Level.INFO, "WebSocket connected: {0}", sessionId);
        });

        ws.onMessage(ctx -> {
            String sessionId = ctx.attribute("sessionId");
            String message = ctx.message();

            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> data = objectMapper.readValue(message, Map.class);
                String action = (String) data.get("action");

                if (action == null) {
                    sendError(ctx, "Missing 'action' field");
                    return;
                }

                switch (action) {
                    case "subscribe" -> handleSubscribe(ctx, sessionId, data);
                    case "unsubscribe" -> handleUnsubscribe(ctx, sessionId, data);
                    case "ping" -> handlePing(ctx);
                    default -> sendError(ctx, "Unknown action: " + action);
                }

            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error processing WebSocket message", e);
                sendError(ctx, "Invalid message format");
            }
        });

        ws.onClose(ctx -> {
            String sessionId = ctx.attribute("sessionId");
            if (sessionId != null) webSocketService.removeSession(sessionId);

            LOGGER.log(Level.INFO, "WebSocket closed: {0} (code: {1}, reason: {2})",
                    new Object[]{sessionId, ctx.status(), ctx.reason()});
        });

        ws.onError(ctx -> {
            String sessionId = ctx.attribute("sessionId");
            LOGGER.log(Level.SEVERE, "WebSocket error for session: " + sessionId, ctx.error());
        });
    }

    private void handleSubscribe(WsContext ctx, String sessionId, Map<String, Object> data) {
        String symbol = (String) data.get("symbol");

        if (symbol == null || symbol.isBlank()) {
            sendError(ctx, "Missing 'symbol' field");
            return;
        }

        webSocketService.subscribe(sessionId, symbol.toUpperCase());

        ctx.send("""
            {
                "type": "subscribed",
                "symbol": "%s",
                "timestamp": %d
            }
            """.formatted(symbol.toUpperCase(), System.currentTimeMillis()));
    }

    private void handleUnsubscribe(WsContext ctx, String sessionId, Map<String, Object> data) {
        String symbol = (String) data.get("symbol");

        if (symbol == null || symbol.isBlank()) {
            sendError(ctx, "Missing 'symbol' field");
            return;
        }

        webSocketService.unsubscribe(sessionId, symbol.toUpperCase());

        ctx.send("""
            {
                "type": "unsubscribed",
                "symbol": "%s",
                "timestamp": %d
            }
            """.formatted(symbol.toUpperCase(), System.currentTimeMillis()));
    }

    private void handlePing(WsContext ctx) {
        ctx.send("""
            {
                "type": "pong",
                "timestamp": %d
            }
            """.formatted(System.currentTimeMillis()));
    }

    private void sendError(WsContext ctx, String message) {
        ctx.send("""
            {
                "type": "error",
                "message": "%s",
                "timestamp": %d
            }
            """.formatted(message, System.currentTimeMillis()));
    }
}