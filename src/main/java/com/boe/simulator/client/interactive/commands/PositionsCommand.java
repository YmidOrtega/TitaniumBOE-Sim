package com.boe.simulator.client.interactive.commands;

import com.boe.simulator.client.interactive.SessionContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public class PositionsCommand implements Command {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

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

            String restUrl = String.format("http://%s:8081/api/positions", context.getHost());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restUrl))
                    .header("Authorization", "Basic " + auth)
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                JsonNode positions = root.get("data");

                if (positions != null && positions.isArray() && positions.size() > 0) printPositions(positions);
                else System.out.println("No positions found");
            } else if (response.statusCode() == 401) System.out.println("✗ Authentication failed");
            else System.out.println("✗ Failed to fetch positions: HTTP " + response.statusCode());
        } catch (Exception e) {
            System.out.println("✗ Error fetching positions: " + e.getMessage());
        }
    }

    private void printPositions(JsonNode positions) {
        System.out.println("\n╔═══════════════════════════════════════════════════════════════════╗");
        System.out.println("║                          Positions                                ║");
        System.out.println("╠═════════╦════════════╦════════════╦═══════════╦══════════════════╣");
        System.out.println("║ Symbol  ║ Quantity   ║   Avg Px   ║ Curr Px   ║   Unrealized P/L ║");
        System.out.println("╠═════════╬════════════╬════════════╬═══════════╬══════════════════╣");

        for (JsonNode pos : positions) {
            String symbol = pos.get("symbol").asText();
            int quantity = pos.get("quantity").asInt();
            double avgPx = pos.get("avgPrice").asDouble();
            double currPx = pos.get("currentPrice").asDouble();
            double pnl = pos.get("unrealizedPnL").asDouble();

            System.out.printf("║ %-7s ║ %10d ║ %10.2f ║ %9.2f ║ %16.2f ║%n", symbol, quantity, avgPx, currPx, pnl);
        }

        System.out.println("╚═════════╩════════════╩════════════╩═══════════╩══════════════════╝\n");
    }

    @Override
    public String getName() {
        return "positions";
    }

    @Override
    public String getUsage() {
        return "positions";
    }

    @Override
    public String getDescription() {
        return "View your positions";
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