package com.boe.simulator.integration;

import com.boe.simulator.client.BoeClient;
import com.boe.simulator.client.interactive.notification.NotificationManager;

import com.boe.simulator.protocol.message.*;
import org.junit.jupiter.api.*;

import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class IntegrationTest {

    private static BoeClient client;
    private static NotificationManager notificationManager;
    private static TestTradingListener tradingListener;

    private static final String HOST = "localhost";
    private static final int PORT = 8081;
    private static final String USERNAME = "TRD1";
    private static final String PASSWORD = "PASS1";

    @BeforeAll
    static void setup() throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   CBOE CLI Integration Test Suite     ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();

        // Initialize notification manager
        notificationManager = new NotificationManager();
        notificationManager.start();

        // Create client
        client = BoeClient.create(HOST, PORT, USERNAME, PASSWORD);

        // Setup trading listener
        tradingListener = new TestTradingListener();
        client.getConnectionHandler().addTradingListener(tradingListener);

        // Connect
        System.out.println("▶ Connecting to " + HOST + ":" + PORT + "...");
        client.connect().get();
        System.out.println("✓ Connected and authenticated\n");
    }

    @Test
    @Order(1)
    @DisplayName("1. Test Connection and Authentication")
    void testConnectionAndAuth() {
        System.out.println("TEST 1: Connection and Authentication");
        assertTrue(client.isConnected(), "Client should be connected");
        assertTrue(client.isAuthenticated(), "Client should be authenticated");
        System.out.println("✓ PASSED\n");
    }

    @Test
    @Order(2)
    @DisplayName("2. Test Order Submission (Passive - No Fill)")
    void testPassiveOrder() throws Exception {
        System.out.println("TEST 2: Passive Order (No Execution)");

        tradingListener.reset();
        tradingListener.expectAcknowledgment();

        // Submit passive buy order below best ask
        NewOrderMessage order = new NewOrderMessage();
        order.setClOrdID("TEST-001");
        order.setSide((byte) 1); // Buy
        order.setOrderQty(100);
        order.setSymbol("AAPL");
        order.setPrice(new BigDecimal("149.50"));
        order.setOrdType((byte) 2); // Limit
        order.setCapacity((byte) 'C');

        client.getConnectionHandler().sendMessageRaw(order.toBytes()).get();

        // Wait for acknowledgment
        boolean ackReceived = tradingListener.ackLatch.await(5, TimeUnit.SECONDS);
        assertTrue(ackReceived, "Should receive order acknowledgment");

        assertNotNull(tradingListener.lastAck, "Acknowledgment should not be null");
        assertEquals("TEST-001", tradingListener.lastAck.getClOrdID());

        System.out.println("✓ Order acknowledged: " + tradingListener.lastAck.getClOrdID());
        System.out.println("✓ PASSED\n");
    }

    @Test
    @Order(3)
    @DisplayName("3. Test Aggressive Order (Should Execute)")
    void testAggressiveOrder() throws Exception {
        System.out.println("TEST 3: Aggressive Order (Execution Expected)");

        tradingListener.reset();
        tradingListener.expectAcknowledgment();
        tradingListener.expectExecution();

        // Submit aggressive buy order at/above best ask
        NewOrderMessage order = new NewOrderMessage();
        order.setClOrdID("TEST-002");
        order.setSide((byte) 1); // Buy
        order.setOrderQty(50);
        order.setSymbol("AAPL");
        order.setPrice(new BigDecimal("150.50"));
        order.setOrdType((byte) 2); // Limit
        order.setCapacity((byte) 'C');

        client.getConnectionHandler().sendMessageRaw(order.toBytes()).get();

        // Wait for acknowledgment and execution
        boolean ackReceived = tradingListener.ackLatch.await(5, TimeUnit.SECONDS);
        boolean execReceived = tradingListener.execLatch.await(5, TimeUnit.SECONDS);

        assertTrue(ackReceived, "Should receive order acknowledgment");
        assertTrue(execReceived, "Should receive order execution");

        assertNotNull(tradingListener.lastExec, "Execution should not be null");
        assertEquals("TEST-002", tradingListener.lastExec.getClOrdID());
        assertEquals(50, tradingListener.lastExec.getLastShares());

        System.out.println("✓ Order executed: " + tradingListener.lastExec.getLastShares() +
                " @ " + tradingListener.lastExec.getLastPx());
        System.out.println("✓ PASSED\n");
    }

    @Test
    @Order(4)
    @DisplayName("4. Test Order Cancellation")
    void testOrderCancellation() throws Exception {
        System.out.println("TEST 4: Order Cancellation");

        // First submit an order
        tradingListener.reset();
        tradingListener.expectAcknowledgment();

        NewOrderMessage order = new NewOrderMessage();
        order.setClOrdID("TEST-003");
        order.setSide((byte) 1);
        order.setOrderQty(100);
        order.setSymbol("AAPL");
        order.setPrice(new BigDecimal("149.00"));
        order.setOrdType((byte) 2);
        order.setCapacity((byte) 'C');

        client.getConnectionHandler().sendMessageRaw(order.toBytes()).get();
        tradingListener.ackLatch.await(5, TimeUnit.SECONDS);

        // Now cancel it
        tradingListener.reset();
        tradingListener.expectCancellation();

        CancelOrderMessage cancel = new CancelOrderMessage("TEST-003");
        client.getConnectionHandler().sendMessageRaw(cancel.toBytes()).get();

        boolean cancelReceived = tradingListener.cancelLatch.await(5, TimeUnit.SECONDS);
        assertTrue(cancelReceived, "Should receive cancellation confirmation");

        assertNotNull(tradingListener.lastCancel);
        assertEquals("TEST-003", tradingListener.lastCancel.getClOrdID());

        System.out.println("✓ Order cancelled: " + tradingListener.lastCancel.getClOrdID());
        System.out.println("✓ PASSED\n");
    }

    @Test
    @Order(5)
    @DisplayName("5. Test Order Rejection (Invalid Symbol)")
    void testOrderRejection() throws Exception {
        System.out.println("TEST 5: Order Rejection");

        tradingListener.reset();
        tradingListener.expectRejection();

        NewOrderMessage order = new NewOrderMessage();
        order.setClOrdID("TEST-004");
        order.setSide((byte) 1);
        order.setOrderQty(100);
        order.setSymbol("INVALID"); // Invalid symbol
        order.setPrice(new BigDecimal("10.00"));
        order.setOrdType((byte) 2);
        order.setCapacity((byte) 'C');

        client.getConnectionHandler().sendMessageRaw(order.toBytes()).get();

        boolean rejectReceived = tradingListener.rejectLatch.await(5, TimeUnit.SECONDS);
        assertTrue(rejectReceived, "Should receive order rejection");

        assertNotNull(tradingListener.lastReject);
        assertEquals("TEST-004", tradingListener.lastReject.getClOrdID());
        assertNotNull(tradingListener.lastReject.getText());

        System.out.println("✓ Order rejected: " + tradingListener.lastReject.getText());
        System.out.println("✓ PASSED\n");
    }

    @AfterAll
    static void tearDown() throws Exception {
        System.out.println("═══════════════════════════════════════");
        System.out.println("Test Summary:");
        System.out.println("  Total Acknowledgments: " + tradingListener.totalAcks);
        System.out.println("  Total Executions: " + tradingListener.totalExecs);
        System.out.println("  Total Cancellations: " + tradingListener.totalCancels);
        System.out.println("  Total Rejections: " + tradingListener.totalRejects);
        System.out.println("═══════════════════════════════════════\n");

        if (client != null) {
            client.disconnect();
        }
        if (notificationManager != null) {
            notificationManager.stop();
        }

        System.out.println("✓ All tests completed successfully!");
    }

    /**
     * Test implementation of TradingMessageListener
     */
    static class TestTradingListener implements com.boe.simulator.client.listener.TradingMessageListener {
        CountDownLatch ackLatch = new CountDownLatch(0);
        CountDownLatch execLatch = new CountDownLatch(0);
        CountDownLatch rejectLatch = new CountDownLatch(0);
        CountDownLatch cancelLatch = new CountDownLatch(0);

        OrderAcknowledgmentMessage lastAck;
        OrderExecutedMessage lastExec;
        OrderRejectedMessage lastReject;
        OrderCancelledMessage lastCancel;

        int totalAcks = 0;
        int totalExecs = 0;
        int totalRejects = 0;
        int totalCancels = 0;

        void reset() {
            lastAck = null;
            lastExec = null;
            lastReject = null;
            lastCancel = null;
        }

        void expectAcknowledgment() {
            ackLatch = new CountDownLatch(1);
        }

        void expectExecution() {
            execLatch = new CountDownLatch(1);
        }

        void expectRejection() {
            rejectLatch = new CountDownLatch(1);
        }

        void expectCancellation() {
            cancelLatch = new CountDownLatch(1);
        }

        @Override
        public void onOrderAcknowledgment(OrderAcknowledgmentMessage message) {
            lastAck = message;
            totalAcks++;
            ackLatch.countDown();
        }

        @Override
        public void onOrderExecuted(OrderExecutedMessage message) {
            lastExec = message;
            totalExecs++;
            execLatch.countDown();
        }

        @Override
        public void onOrderRejected(OrderRejectedMessage message) {
            lastReject = message;
            totalRejects++;
            rejectLatch.countDown();
        }

        @Override
        public void onOrderCancelled(OrderCancelledMessage message) {
            lastCancel = message;
            totalCancels++;
            cancelLatch.countDown();
        }
    }
}