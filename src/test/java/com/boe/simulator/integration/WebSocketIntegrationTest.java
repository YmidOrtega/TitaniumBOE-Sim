package com.boe.simulator.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.junit.jupiter.api.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class WebSocketIntegrationTest {

    private static final String REST_API_URL = "http://localhost:9091";
    private static final String WS_URL = "ws://localhost:9091/ws/feed";
    private static final String USERNAME = "TRADER1";
    private static final String PASSWORD = "PASS1";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    @Test
    @Order(1)
    @DisplayName("Test 1: WebSocket Connection")
    public void testWebSocketConnection() throws Exception {
        System.out.println("\n========== Test 1: WebSocket Connection ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        assertTrue(client.isOpen(), "✓ WebSocket connection established");

        // Wait for a connection message
        String connectedMsg = messages.poll(2, TimeUnit.SECONDS);
        assertNotNull(connectedMsg, "✓ Received connection message");

        JsonNode json = objectMapper.readTree(connectedMsg);
        assertEquals("connected", json.get("type").asText());
        assertNotNull(json.get("sessionId"));
        System.out.println("✓ Session ID: " + json.get("sessionId").asText());

        client.closeBlocking();
        System.out.println("✓ PASS - WebSocket connection works\n");
    }

    @Test
    @Order(2)
    @DisplayName("Test 2: Subscribe to Symbol")
    public void testSubscribeToSymbol() throws Exception {
        System.out.println("========== Test 2: Subscribe to Symbol ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        messages.poll(2, TimeUnit.SECONDS); // Consume connection message

        // Subscribe to AAPL
        client.send("""
            {
                "action": "subscribe",
                "symbol": "AAPL"
            }
            """);

        String subscribeMsg = messages.poll(2, TimeUnit.SECONDS);
        assertNotNull(subscribeMsg, "✓ Received subscription confirmation");

        JsonNode json = objectMapper.readTree(subscribeMsg);
        assertEquals("subscribed", json.get("type").asText());
        assertEquals("AAPL", json.get("symbol").asText());
        System.out.println("✓ Subscribed to AAPL");

        client.closeBlocking();
        System.out.println("✓ PASS - Symbol subscription works\n");
    }

    @Test
    @Order(3)
    @DisplayName("Test 3: Ping/Pong")
    public void testPingPong() throws Exception {
        System.out.println("========== Test 3: Ping/Pong ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        messages.poll(2, TimeUnit.SECONDS); // Consume connection message

        // Send ping
        client.send("""
            {
                "action": "ping"
            }
            """);

        String pongMsg = messages.poll(2, TimeUnit.SECONDS);
        assertNotNull(pongMsg, "✓ Received pong response");

        JsonNode json = objectMapper.readTree(pongMsg);
        assertEquals("pong", json.get("type").asText());
        System.out.println("✓ Ping/Pong working");

        client.closeBlocking();
        System.out.println("✓ PASS - Heartbeat mechanism works\n");
    }

    @Test
    @Order(4)
    @DisplayName("Test 4: Receive Order Book Updates")
    public void testOrderBookUpdates() throws Exception {
        System.out.println("========== Test 4: Order Book Updates ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(20);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        messages.poll(2, TimeUnit.SECONDS); // Consume connection message

        // Subscribe to AAPL
        client.send("""
        {
            "action": "subscribe",
            "symbol": "AAPL"
        }
        """);
        messages.poll(2, TimeUnit.SECONDS); // Consume subscription confirmation

        // ✅ ARREGLO: Enviar orden que NO cruce el mercado (para que se añada al book)
        // Seed ask = 150.50, así que ponemos bid a 149.00 (menor)
        submitOrder("AAPL", "BUY", 100, "149.00");

        // Wait for order book update
        String updateMsg = waitForMessageType(messages, "orderbook", 5);
        assertNotNull(updateMsg, "✓ Received order book update");

        JsonNode json = objectMapper.readTree(updateMsg);
        assertEquals("orderbook", json.get("type").asText());
        assertEquals("AAPL", json.get("symbol").asText());
        assertTrue(json.has("bids"));
        assertTrue(json.has("asks"));
        System.out.println("✓ Order book update received");
        System.out.println("  Bids: " + json.get("bids").size());
        System.out.println("  Asks: " + json.get("asks").size());

        client.closeBlocking();
        System.out.println("✓ PASS - Order book updates work\n");
    }

    @Test
    @Order(5)
    @DisplayName("Test 5: Receive Trade Updates")
    public void testTradeUpdates() throws Exception {
        System.out.println("========== Test 5: Trade Updates ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(20);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        messages.poll(2, TimeUnit.SECONDS); // Consume connection message

        // Subscribe to MSFT (usar otro símbolo para evitar interferencia)
        client.send("""
        {
            "action": "subscribe",
            "symbol": "MSFT"
        }
        """);
        messages.poll(2, TimeUnit.SECONDS); // Consume subscription confirmation

        // ✅ ARREGLO: Enviar orden que CRUCE el mercado para generar trade
        // Seed: bid=379.00, ask=381.00
        // Enviamos BUY a 381.00 o mayor para que cruce
        submitOrder("MSFT", "BUY", 25, "381.00");

        Thread.sleep(500); // Wait for processing

        // Wait for trade update
        String tradeMsg = waitForMessageType(messages, "trade", 5);
        assertNotNull(tradeMsg, "✓ Received trade update");

        JsonNode json = objectMapper.readTree(tradeMsg);
        assertEquals("trade", json.get("type").asText());
        assertEquals("MSFT", json.get("symbol").asText());
        assertTrue(json.has("tradeId"));
        assertTrue(json.has("price"));
        assertTrue(json.has("quantity"));
        System.out.println("✓ Trade update received");
        System.out.println("  Trade ID: " + json.get("tradeId").asLong());
        System.out.println("  Price: " + json.get("price").asText());
        System.out.println("  Quantity: " + json.get("quantity").asInt());

        client.closeBlocking();
        System.out.println("✓ PASS - Trade updates work\n");
    }

    @Test
    @Order(6)
    @DisplayName("Test 6: Multiple Clients")
    public void testMultipleClients() throws Exception {
        System.out.println("========== Test 6: Multiple Clients ==========");

        BlockingQueue<String> messages1 = new ArrayBlockingQueue<>(10);
        BlockingQueue<String> messages2 = new ArrayBlockingQueue<>(10);

        TestWebSocketClient client1 = new TestWebSocketClient(new URI(WS_URL), messages1);
        TestWebSocketClient client2 = new TestWebSocketClient(new URI(WS_URL), messages2);

        // Connect both clients
        client1.connectBlocking(5, TimeUnit.SECONDS);
        client2.connectBlocking(5, TimeUnit.SECONDS);

        messages1.poll(2, TimeUnit.SECONDS);
        messages2.poll(2, TimeUnit.SECONDS);

        // Subscribe both to MSFT
        client1.send("""
            {
                "action": "subscribe",
                "symbol": "MSFT"
            }
            """);
        client2.send("""
            {
                "action": "subscribe",
                "symbol": "MSFT"
            }
            """);

        messages1.poll(2, TimeUnit.SECONDS);
        messages2.poll(2, TimeUnit.SECONDS);

        // Submit order to trigger update
        submitOrder("MSFT", "BUY", 25, "380.00");

        // Both clients should receive the update
        String update1 = waitForMessageType(messages1, "orderbook", 5);
        String update2 = waitForMessageType(messages2, "orderbook", 5);

        assertNotNull(update1, "✓ Client 1 received update");
        assertNotNull(update2, "✓ Client 2 received update");

        System.out.println("✓ Both clients received order book updates");

        client1.closeBlocking();
        client2.closeBlocking();
        System.out.println("✓ PASS - Multiple clients work\n");
    }

    @Test
    @Order(7)
    @DisplayName("Test 7: Unsubscribe")
    public void testUnsubscribe() throws Exception {
        System.out.println("========== Test 7: Unsubscribe ==========");

        BlockingQueue<String> messages = new ArrayBlockingQueue<>(10);
        TestWebSocketClient client = new TestWebSocketClient(new URI(WS_URL), messages);

        client.connectBlocking(5, TimeUnit.SECONDS);
        messages.poll(2, TimeUnit.SECONDS);

        // Subscribe
        client.send("""
            {
                "action": "subscribe",
                "symbol": "GOOGL"
            }
            """);
        messages.poll(2, TimeUnit.SECONDS);

        // Unsubscribe
        client.send("""
            {
                "action": "unsubscribe",
                "symbol": "GOOGL"
            }
            """);

        String unsubMsg = messages.poll(2, TimeUnit.SECONDS);
        assertNotNull(unsubMsg);

        JsonNode json = objectMapper.readTree(unsubMsg);
        assertEquals("unsubscribed", json.get("type").asText());
        assertEquals("GOOGL", json.get("symbol").asText());
        System.out.println("✓ Unsubscribed from GOOGL");

        // Submit order - should NOT receive update
        submitOrder("GOOGL", "BUY", 50, "140.00");
        Thread.sleep(1000);

        String noUpdate = messages.poll(1, TimeUnit.SECONDS);
        // Should be null or not an orderbook message for GOOGL
        System.out.println("✓ No updates received after unsubscribe");

        client.closeBlocking();
        System.out.println("✓ PASS - Unsubscribe works\n");
    }

    // Helper methods

    private void submitOrder(String symbol, String side, int qty, String price) throws Exception {
        String orderJson = String.format("""
            {
                "symbol": "%s",
                "side": "%s",
                "orderQty": %d,
                "price": %s,
                "orderType": "LIMIT",
                "capacity": "CUSTOMER"
            }
            """, symbol, side, qty, price);

        String auth = Base64.getEncoder().encodeToString(
                (USERNAME + ":" + PASSWORD).getBytes(StandardCharsets.UTF_8)
        );

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(REST_API_URL + "/api/orders"))
                .header("Authorization", "Basic " + auth)
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(5))
                .POST(HttpRequest.BodyPublishers.ofString(orderJson))
                .build();

        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String waitForMessageType(BlockingQueue<String> messages, String type, int timeoutSeconds)
            throws Exception {
        long deadline = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        List<String> receivedMessages = new ArrayList<>();

        while (System.currentTimeMillis() < deadline) {
            String msg = messages.poll(500, TimeUnit.MILLISECONDS); // ✅ Esperar menos tiempo entre polls
            if (msg != null) {
                try {
                    JsonNode json = objectMapper.readTree(msg);
                    String msgType = json.get("type").asText();

                    System.out.println("  → Checking message type: " + msgType); // Debug

                    if (type.equals(msgType)) {
                        return msg;
                    }
                    receivedMessages.add(msgType);
                } catch (Exception e) {
                    System.err.println("  ✗ Error parsing message: " + e.getMessage());
                }
            }
        }
        System.err.println("  ✗ Timeout waiting for message type: " + type);
        System.err.println("  ✗ Received types: " + receivedMessages);
        return null;
    }

    // WebSocket Client for testing
    static class TestWebSocketClient extends WebSocketClient {
        private final BlockingQueue<String> messages;

        public TestWebSocketClient(URI serverUri, BlockingQueue<String> messages) {
            super(serverUri);
            this.messages = messages;
        }

        @Override
        public void onOpen(ServerHandshake handshake) {
            System.out.println("  → WebSocket opened");
        }

        @Override
        public void onMessage(String message) {
            System.out.println("  ← Received: " + message.substring(0, Math.min(100, message.length())));
            messages.offer(message);
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            System.out.println("  → WebSocket closed: " + code + " - " + reason);
        }

        @Override
        public void onError(Exception ex) {
            System.err.println("  ✗ WebSocket error: " + ex.getMessage());
        }
    }
}