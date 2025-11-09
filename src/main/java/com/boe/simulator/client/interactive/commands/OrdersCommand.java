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

public class OrdersCommand implements Command {

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void execute(SessionContext context, String[] args) throws Exception {
        String username = context.getUsername();
        String password = (String) context.get("password");

        if (password == null) {
            System.out.println("✗ Password not available in session");
            return;
        }

        String auth = Base64.getEncoder().encodeToString(
                (username + ":" + password).getBytes(StandardCharsets.UTF_8)
        );

        String restUrl = String.format("http://%s:9091/api/orders/active", context.getHost());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(restUrl))
                .header("Authorization", "Basic " + auth)
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            JsonNode root = objectMapper.readTree(response.body());
            JsonNode orders = root.get("data");

            if (orders != null && orders.isArray() && orders.size() > 0) printOrders(orders);
            else System.out.println("No active orders");
        } else if (response.statusCode() == 401) System.out.println("✗ Authentication failed");
        else System.out.println("✗ Failed to fetch orders: HTTP " + response.statusCode());

    }

    private void printOrders(JsonNode orders) {
        System.out.println("\n╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                        Active Orders                               ║");
        System.out.println("╠═══════════════╦═════════╦══════╦═════════╦═════════╦══════════════╣");
        System.out.println("║   ClOrdID     ║ Symbol  ║ Side ║   Qty   ║  Price  ║    Status    ║");
        System.out.println("╠═══════════════╬═════════╬══════╬═════════╬═════════╬══════════════╣");

        for (JsonNode order : orders) {
            String clOrdID = order.get("clOrdID").asText();
            String symbol = order.get("symbol").asText();
            String side = order.get("side").asText();
            int qty = order.get("leavesQty").asInt();
            double price = order.get("price").asDouble();
            String status = order.get("state").asText();

            System.out.printf("║ %-13s ║ %-7s ║ %-4s ║ %7d ║ %7.2f ║ %-12s ║%n",
                    truncate(clOrdID, 13), symbol, side, qty, price, status);
        }

        System.out.println("╚═══════════════╩═════════╩══════╩═════════╩═════════╩══════════════╝\n");
    }

    private String truncate(String str, int maxLen) {
        return str.length() > maxLen ? str.substring(0, maxLen - 3) + "..." : str;
    }

    @Override
    public String getName() {
        return "orders";
    }

    @Override
    public String getUsage() {
        return "orders";
    }

    @Override
    public String getDescription() {
        return "View active orders";
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