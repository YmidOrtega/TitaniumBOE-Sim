package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class BookCommand implements Command {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(SessionContext context, String[] args) {
        try {
            if (args.length < 1) {
                System.out.println("Usage: " + getUsage());
                return;
            }

            String symbol = args[0].toUpperCase();
            int depth = args.length > 1 ? Integer.parseInt(args[1]) : 5;

            // Use REST API to get an order book
            String restUrl = String.format("http://%s:9091/api/symbols", context.getHost());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode data = root.get("data");

                if (data != null && data.isArray()) {
                    for (JsonNode symbolData : data) {
                        if (symbol.equals(symbolData.get("symbol").asText())) {
                            printOrderBook(symbolData, depth);
                            return;
                        }
                    }
                }

                System.out.println("Symbol not found: " + symbol);
            } else System.out.println("✗ Failed to fetch order book: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.out.println("✗ Error fetching order book: " + e.getMessage());
        }
    }

    private void printOrderBook(JsonNode symbolData, int depth) {
        String symbol = symbolData.get("symbol").asText();
        JsonNode bestBid = symbolData.get("bestBid");
        JsonNode bestAsk = symbolData.get("bestAsk");

        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.printf("║        Order Book: %-19s ║%n", symbol);
        System.out.println("╠════════════════════════════════════════╣");
        System.out.printf("║ Best Bid: %-28s ║%n", bestBid != null ? bestBid.asText() : "N/A");
        System.out.printf("║ Best Ask: %-28s ║%n", bestAsk != null ? bestAsk.asText() : "N/A");

        if (bestBid != null && bestAsk != null && !bestBid.isNull() && !bestAsk.isNull()) {
            double spread = bestAsk.asDouble() - bestBid.asDouble();
            System.out.printf("║ Spread:   %-28.2f ║%n", spread);
        }

        System.out.println("╚════════════════════════════════════════╝\n");
    }

    @Override
    public String getName() {
        return "book";
    }

    @Override
    public String getUsage() {
        return "book <symbol> [depth]";
    }

    @Override
    public String getDescription() {
        return "View order book for a symbol";
    }

    @Override
    public boolean requiresConnection() {
        return true;
    }
}