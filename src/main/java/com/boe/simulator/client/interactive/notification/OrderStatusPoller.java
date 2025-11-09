package com.boe.simulator.client.interactive.notification;

import com.boe.simulator.client.interactive.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class OrderStatusPoller {

    private final SessionContext context;
    private final NotificationManager notificationManager;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final ScheduledExecutorService scheduler;
    private final Set<String> seenTrades;

    public OrderStatusPoller(SessionContext context, NotificationManager notificationManager) {
        this.context = context;
        this.notificationManager = notificationManager;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.scheduler = Executors.newSingleThreadScheduledExecutor();
        this.seenTrades = new HashSet<>();
    }

    public void start() {
        // Poll every 2 seconds for new trades
        scheduler.scheduleAtFixedRate(this::pollTrades, 2, 2, TimeUnit.SECONDS);
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void pollTrades() {
        if (!context.isAuthenticated()) return;

        try {
            String username = context.getUsername();
            String password = (String) context.get("password");

            if (password == null) return;

            String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));

            String url = String.format("http://%s:9091/api/trades/my", context.getHost());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", "Basic " + auth)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode trades = root.get("data");

                if (trades != null && trades.isArray()) {
                    for (JsonNode trade : trades) {
                        String tradeId = trade.get("tradeId").asText();

                        if (!seenTrades.contains(tradeId)) {
                            seenTrades.add(tradeId);

                            // Notify about new trade
                            String symbol = trade.get("symbol").asText();
                            String side = trade.get("side").asText();
                            int qty = trade.get("quantity").asInt();
                            double price = trade.get("price").asDouble();

                            String message = String.format("%s %d %s @ %.2f", side, qty, symbol, price);

                            notificationManager.notify(NotificationManager.NotificationType.ORDER_EXECUTED, message);
                        }
                    }
                }
            }
        } catch (Exception e) {

        }
    }
}