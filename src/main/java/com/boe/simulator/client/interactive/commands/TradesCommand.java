package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;

public class TradesCommand implements Command {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    @Override
    public void execute(SessionContext context, String[] args) {
        try {
            String username = context.getUsername();
            String password = (String) context.get("password");

            if (password == null) {
                System.out.println("✗ Password not available in session. Please login again.");
                return;
            }

            String auth = Base64.getEncoder().encodeToString(
                    (username + ":" + password).getBytes(StandardCharsets.UTF_8)
            );

            String restUrl = String.format("http://%s:8081/api/trades/my", context.getHost());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl))
                    .header("Authorization", "Basic " + auth)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode trades = root.get("data");

                if (trades != null && trades.isArray() && trades.size() > 0) printTrades(trades);
                else System.out.println("No trades found");
            } else if (response.statusCode() == 401) System.out.println("✗ Authentication failed");
            else System.out.println("✗ Failed to fetch trades: HTTP " + response.statusCode());

        } catch (Exception e) {
            System.out.println("✗ Error fetching trades: " + e.getMessage());
        }
    }

    private void printTrades(JsonNode trades) {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                          Recent Trades                               ║");
        System.out.println("╠══════════╦═════════╦═════════╦═════════╦═════════════════════════════╣");
        System.out.println("║   Time   ║ Symbol  ║   Qty   ║  Price  ║          Value            ║");
        System.out.println("╠══════════╬═════════╬═════════╬═════════╬═════════════════════════════╣");

        for (JsonNode trade : trades) {
            double execTime = trade.get("executionTime").asDouble();
            long timestamp = (long) (execTime * 1000); // Convert seconds to milliseconds
            String time = timeFormatter.format(Instant.ofEpochMilli(timestamp));
            String symbol = trade.get("symbol").asText();
            int qty = trade.get("quantity").asInt();
            double price = trade.get("price").asDouble();
            double value = trade.get("notionalValue").asDouble();

            System.out.printf("║ %8s ║ %-7s ║ %7d ║ %7.2f ║ %25.2f ║%n", time, symbol, qty, price, value);
        }

        System.out.println("╚══════════╩═════════╩═════════╩═════════╩═════════════════════════════╝\n");
    }

    @Override
    public String getName() {
        return "trades";
    }

    @Override
    public String getUsage() {
        return "trades";
    }

    @Override
    public String getDescription() {
        return "View your recent trades";
    }

    @Override
    public boolean requiresConnection() {
        return true;
    }

    @Override
    public boolean requiresAuthentication() {
        return true;
    }
}