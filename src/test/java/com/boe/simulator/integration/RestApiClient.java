package com.boe.simulator.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Locale;

public class RestApiClient {
    private final String baseUrl;
    private final String username;
    private final String password;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public RestApiClient(String baseUrl, String username, String password) {
        this.baseUrl = baseUrl;
        this.username = username;
        this.password = password;
        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    public String submitOrder(String symbol, String side, int qty, double price) throws IOException, InterruptedException {
        //FIX: Usar Locale.US para asegurar punto decimal
        String json = String.format(Locale.US, """
                {
                    "symbol": "%s",
                    "side": "%s",
                    "orderQty": %d,
                    "price": %.2f,
                    "orderType": "LIMIT",
                    "capacity": "CUSTOMER"
                }
                """, symbol, side, qty, price);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders"))
                .header("Authorization", getAuthHeader())
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String getActiveOrders() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/active"))
                .header("Authorization", getAuthHeader())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String getPositions() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/positions"))
                .header("Authorization", getAuthHeader())
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    public String cancelOrder(String clOrdID) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/orders/" + clOrdID))
                .header("Authorization", getAuthHeader())
                .DELETE()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.body();
    }

    private String getAuthHeader() {
        String credentials = username + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
    }

    public static void main(String[] args) throws Exception {
        RestApiClient client = new RestApiClient("http://localhost:9090", "TRADER1", "PASS1");
        ObjectMapper mapper = new ObjectMapper();

        System.out.println("Submitting order...");
        String orderResponse = client.submitOrder("AAPL", "BUY", 100, 150.00);
        System.out.println(orderResponse);

        // Extract clOrdID from the response
        String clOrdID = mapper.readTree(orderResponse).get("clOrdID").asText();
        System.out.println("\nOrder submitted with clOrdID: " + clOrdID);

        System.out.println("\n⏳ Waiting 1 second...\n");
        Thread.sleep(1000);

        System.out.println("Active orders:");
        System.out.println(client.getActiveOrders());

        System.out.println("\nCancelling order: " + clOrdID);
        String cancelResponse = client.cancelOrder(clOrdID);
        System.out.println(cancelResponse);

        System.out.println("\n⏳ Waiting 1 second...\n");
        Thread.sleep(1000);

        System.out.println("Active orders (after cancellation):");
        System.out.println(client.getActiveOrders());

        System.out.println("\nPositions:");
        System.out.println(client.getPositions());
    }
}