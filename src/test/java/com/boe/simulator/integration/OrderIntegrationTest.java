package com.boe.simulator.integration;

import com.boe.simulator.client.connection.BoeConnectionHandler;
import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.*;
import com.boe.simulator.protocol.types.BinaryPrice;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Integración: Sistema de Órdenes BOE            ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        // Test 1: New Order - Accepted
        testNewOrderAccepted();
        Thread.sleep(2000);

        // Test 2: New Order - Rejected (duplicate)
        testNewOrderRejected();
        Thread.sleep(2000);

        // Test 3: Cancel Order
        testCancelOrder();
        Thread.sleep(2000);

        // Test 4: Multiple Orders
        testMultipleOrders();
        Thread.sleep(2000);

        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Tests Completados - Verifica logs del servidor     ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    private static void testNewOrderAccepted() throws Exception {
        System.out.println("═══ Test 1: New Order - Accepted ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        AtomicInteger orderAcks = new AtomicInteger(0);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();
                if (messageType == 0x25) { // OrderAcknowledgment
                    orderAcks.incrementAndGet();
                    System.out.println("✓✓ OrderAcknowledgment received!");
                    parseOrderAck(message);
                } else if (messageType == 0x26) { // OrderRejected
                    System.out.println("✗✗ OrderRejected received (unexpected)");
                    parseOrderRejected(message);
                }
            }
        });

        // Connect and login
        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD1");
        client.sendMessageRaw(login.toBytes()).get();
        Thread.sleep(1000);

        // Send NewOrder
        System.out.println("Sending NewOrder...");
        byte[] newOrderBytes = buildNewOrderMessage(
                "TEST001",      // ClOrdID
                (byte)1,        // Side: Buy
                100,            // Qty
                new BigDecimal("150.50"),  // Price
                "AAPL",         // Symbol
                (byte)'C'       // Capacity: Customer
        );

        client.sendMessageRaw(newOrderBytes).get();
        System.out.println("✓ NewOrder sent: ClOrdID=TEST001, Symbol=AAPL, Side=Buy, Qty=100, Price=150.50");

        // Wait for response
        Thread.sleep(2000);

        // Cleanup
        client.stopListener();
        client.disconnect().get();

        System.out.println("\n✓ Test 1 completed - Acknowledgments received: " + orderAcks.get());
        System.out.println();
    }

    private static void testNewOrderRejected() throws Exception {
        System.out.println("═══ Test 2: New Order - Rejected (Duplicate) ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();
                if (messageType == 0x26) { // OrderRejected
                    System.out.println("✓✓ OrderRejected received (as expected)");
                    parseOrderRejected(message);
                }
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD2");
        client.sendMessageRaw(login.toBytes()).get();
        Thread.sleep(1000);

        // Send same ClOrdID twice
        byte[] order1 = buildNewOrderMessage("DUP001", (byte)1, 50, new BigDecimal("100.00"), "MSFT", (byte)'C');
        client.sendMessageRaw(order1).get();
        System.out.println("✓ First order sent: ClOrdID=DUP001");
        Thread.sleep(500);

        byte[] order2 = buildNewOrderMessage("DUP001", (byte)2, 50, new BigDecimal("100.50"), "MSFT", (byte)'C');
        client.sendMessageRaw(order2).get();
        System.out.println("✓ Duplicate order sent: ClOrdID=DUP001 (should be rejected)");

        Thread.sleep(2000);

        client.stopListener();
        client.disconnect().get();

        System.out.println("\n✓ Test 2 completed");
        System.out.println();
    }

    private static void testCancelOrder() throws Exception {
        System.out.println("═══ Test 3: Cancel Order ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();
                if (messageType == 0x25) {
                    System.out.println("✓ OrderAcknowledgment received");
                } else if (messageType == 0x28) { // OrderCancelled
                    System.out.println("✓✓ OrderCancelled received!");
                    parseOrderCancelled(message);
                }
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD3");
        client.sendMessageRaw(login.toBytes()).get();
        Thread.sleep(1000);

        // Send NewOrder
        byte[] newOrder = buildNewOrderMessage("CANCEL001", (byte)1, 200, new BigDecimal("75.25"), "GOOGL", (byte)'F');
        client.sendMessageRaw(newOrder).get();
        System.out.println("✓ NewOrder sent: ClOrdID=CANCEL001");
        Thread.sleep(1000);

        // Send CancelOrder
        byte[] cancelOrder = buildCancelOrderMessage("CANCEL001");
        client.sendMessageRaw(cancelOrder).get();
        System.out.println("✓ CancelOrder sent for ClOrdID=CANCEL001");

        Thread.sleep(2000);

        client.stopListener();
        client.disconnect().get();

        System.out.println("\n✓ Test 3 completed");
        System.out.println();
    }

    private static void testMultipleOrders() throws Exception {
        System.out.println("═══ Test 4: Multiple Orders ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);
        AtomicInteger ackCount = new AtomicInteger(0);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                System.out.println("✓ Login: " + (char)response.getLoginResponseStatus());
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                if (message.getMessageType() == 0x25) {
                    ackCount.incrementAndGet();
                }
            }
        });

        client.connect().get();
        client.startListener();
        Thread.sleep(300);

        LoginRequestMessage login = new LoginRequestMessage("TEST", "TEST", "ORD4");
        client.sendMessageRaw(login.toBytes()).get();
        Thread.sleep(1000);

        // Send multiple orders
        String[] symbols = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"};
        for (int i = 0; i < 5; i++) {
            byte[] order = buildNewOrderMessage(
                    "MULTI" + String.format("%03d", i),
                    (byte)(i % 2 == 0 ? 1 : 2),  // Alternate Buy/Sell
                    (i + 1) * 10,
                    new BigDecimal(100 + i * 10),
                    symbols[i],
                    (byte)'C'
            );
            client.sendMessageRaw(order).get();
            System.out.println("✓ Order " + (i+1) + " sent: " + symbols[i]);
            Thread.sleep(200);
        }

        Thread.sleep(2000);

        client.stopListener();
        client.disconnect().get();

        System.out.println("\n✓ Test 4 completed - Acknowledgments: " + ackCount.get() + "/5");
        System.out.println();
    }

    private static byte[] buildNewOrderMessage(String clOrdID, byte side, int qty,
                                               BigDecimal price, String symbol, byte capacity) {
        // Simplified version - just required fields
        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1 + 4 + 1; // Header + required
        int bitfieldsSize = 2; // 2 bitfields
        int optionalSize = 8 + 8 + 1; // Price + Symbol + Capacity

        ByteBuffer buffer = ByteBuffer.allocate(baseSize + bitfieldsSize + optionalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);

        // MessageLength
        buffer.putShort((short)(baseSize - 4 + bitfieldsSize + optionalSize));

        // MessageType
        buffer.put((byte) 0x38);

        // MatchingUnit
        buffer.put((byte) 0);

        // SequenceNumber
        buffer.putInt(1);

        // ClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(clOrdID, 20));

        // Side
        buffer.put(side);

        // OrderQty
        buffer.putInt(qty);

        // NumberOfBitfields
        buffer.put((byte) 2);

        // Bitfield 1: Price (bit 2) + Symbol (bit 6) + Capacity (bit 7)
        buffer.put((byte) (0x04 | 0x40 | 0x80));

        // Bitfield 2: empty
        buffer.put((byte) 0x00);

        // Price
        buffer.put(BinaryPrice.fromPrice(price).toBytes());

        // Symbol (8 bytes)
        buffer.put(toFixedLengthBytes(symbol, 8));

        // Capacity
        buffer.put(capacity);

        return buffer.array();
    }

    private static byte[] buildCancelOrderMessage(String origClOrdID) {
        int totalSize = 2 + 2 + 1 + 1 + 4 + 20 + 1;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);

        // MessageLength
        buffer.putShort((short)(totalSize - 2));

        // MessageType
        buffer.put((byte) 0x39);

        // MatchingUnit
        buffer.put((byte) 0);

        // SequenceNumber
        buffer.putInt(1);

        // OrigClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(origClOrdID, 20));

        // NumberOfBitfields
        buffer.put((byte) 0);

        return buffer.array();
    }

    private static byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 0x20);

        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            int copyLength = Math.min(strBytes.length, length);
            System.arraycopy(strBytes, 0, result, 0, copyLength);
        }

        return result;
    }

    private static void parseOrderAck(BoeMessage message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getData()).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(10); // Skip to TransactTime

            buffer.getLong(); // TransactTime

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            long orderID = buffer.getLong();

            System.out.println("  → ClOrdID: " + clOrdID);
            System.out.println("  → OrderID: " + orderID);
        } catch (Exception e) {
            System.out.println("  Error parsing: " + e.getMessage());
        }
    }

    private static void parseOrderRejected(BoeMessage message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getData()).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(10); // Skip to TransactTime

            buffer.getLong(); // TransactTime

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            byte reason = buffer.get();

            byte[] textBytes = new byte[60];
            buffer.get(textBytes);
            String text = new String(textBytes, StandardCharsets.US_ASCII).trim();

            System.out.println("  → ClOrdID: " + clOrdID);
            System.out.println("  → Reason: " + (char)reason);
            System.out.println("  → Text: " + text);
        } catch (Exception e) {
            System.out.println("  Error parsing: " + e.getMessage());
        }
    }

    private static void parseOrderCancelled(BoeMessage message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getData()).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(10);

            buffer.getLong(); // TransactTime

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            byte reason = buffer.get();

            System.out.println("  → ClOrdID: " + clOrdID);
            System.out.println("  → Reason: " + (char)reason);
        } catch (Exception e) {
            System.out.println("  Error parsing: " + e.getMessage());
        }
    }
}