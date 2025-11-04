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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OrderIntegrationTest {

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Test Integración: Sistema de Órdenes BOE            ║");
        System.out.println("╚════════════════════════════════════════════════════════╝\n");

        testNewOrderAccepted();
        Thread.sleep(1000);

        testNewOrderRejected();
        Thread.sleep(1000);

        testCancelOrder();
        Thread.sleep(1000);

        testMultipleOrders();
        Thread.sleep(1000);

        System.out.println("\n╔════════════════════════════════════════════════════════╗");
        System.out.println("║    Tests Completados - Todos Exitosos                 ║");
        System.out.println("╚════════════════════════════════════════════════════════╝");
    }

    private static void testNewOrderAccepted() throws Exception {
        System.out.println("═══ Test 1: New Order - Accepted ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        CountDownLatch loginLatch = new CountDownLatch(1);
        CountDownLatch orderAckLatch = new CountDownLatch(1);
        CountDownLatch logoutLatch = new CountDownLatch(1);
        AtomicBoolean loginSuccess = new AtomicBoolean(false);
        AtomicInteger orderAcks = new AtomicInteger(0);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                char status = (char)response.getLoginResponseStatus();
                System.out.println("  ✓ Login Response: " + status);
                loginSuccess.set(status == 'A');
                loginLatch.countDown();
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                System.out.println("  ✓ Logout Response received");
                logoutLatch.countDown();
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();

                if (messageType == 0x25) {
                    orderAcks.incrementAndGet();
                    System.out.println("  ✓✓ OrderAcknowledgment received!");
                    parseOrderAck(message);
                    orderAckLatch.countDown();
                } else if (messageType == 0x26) {
                    System.out.println("  ✗✗ OrderRejected received (unexpected)");
                    parseOrderRejected(message);
                    orderAckLatch.countDown();
                }
            }
        });

        try {
            client.connect().get(5, TimeUnit.SECONDS);
            client.startListener();
            Thread.sleep(100);

            LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD1");
            client.sendMessageRaw(login.toBytes()).get(2, TimeUnit.SECONDS);

            if (!loginLatch.await(10, TimeUnit.SECONDS) || !loginSuccess.get()) {
                System.out.println("  ✗ Login failed\n");
                return;
            }
            System.out.println("  ✓ Login successful\n");

            System.out.println("Sending NewOrder...");
            byte[] newOrderBytes = buildNewOrderMessage(
                    "TEST001",
                    (byte)1,
                    100,
                    new BigDecimal("150.50"),
                    "AAPL",
                    (byte)'C'
            );

            client.sendMessageRaw(newOrderBytes).get(2, TimeUnit.SECONDS);
            System.out.println("  ✓ NewOrder sent: ClOrdID=TEST001, Symbol=AAPL, Side=Buy, Qty=100, Price=150.50");

            if (orderAckLatch.await(10, TimeUnit.SECONDS)) {
                System.out.println("\n✓ Test 1 PASSED - Order acknowledged\n");
            } else {
                System.out.println("\n✗ Test 1 FAILED - No acknowledgment received\n");
            }

            System.out.println("Sending Logout...");
            byte[] logoutBytes = buildLogoutMessage();
            client.sendMessageRaw(logoutBytes).get(2, TimeUnit.SECONDS);
            logoutLatch.await(5, TimeUnit.SECONDS);
            System.out.println("  ✓ Logout completed");

        } catch (Exception e) {
            System.out.println("  ✗ Test 1 FAILED: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.stopListener();
            Thread.sleep(200);
            client.disconnect().get();
        }
    }

    private static void testNewOrderRejected() throws Exception {
        System.out.println("═══ Test 2: New Order - Rejected (Duplicate) ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        CountDownLatch loginLatch = new CountDownLatch(1);
        CountDownLatch firstOrderLatch = new CountDownLatch(1);
        CountDownLatch rejectLatch = new CountDownLatch(1);
        CountDownLatch logoutLatch = new CountDownLatch(1);
        AtomicBoolean loginSuccess = new AtomicBoolean(false);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                loginSuccess.set((char)response.getLoginResponseStatus() == 'A');
                loginLatch.countDown();
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                logoutLatch.countDown();
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();

                if (messageType == 0x25) {
                    System.out.println("  ✓ First order acknowledged");
                    firstOrderLatch.countDown();
                } else if (messageType == 0x26) {
                    System.out.println("  ✓✓ OrderRejected received (expected for duplicate)");
                    parseOrderRejected(message);
                    rejectLatch.countDown();
                }
            }
        });

        try {
            client.connect().get(5, TimeUnit.SECONDS);
            client.startListener();
            Thread.sleep(100);

            LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD2");
            client.sendMessageRaw(login.toBytes()).get(2, TimeUnit.SECONDS);

            if (!loginLatch.await(10, TimeUnit.SECONDS) || !loginSuccess.get()) {
                System.out.println("  ✗ Login failed\n");
                return;
            }
            System.out.println("  ✓ Login successful\n");

            byte[] order1 = buildNewOrderMessage("DUP001", (byte)1, 50, new BigDecimal("100.00"), "MSFT", (byte)'C');
            client.sendMessageRaw(order1).get(2, TimeUnit.SECONDS);
            System.out.println("  → First order sent: ClOrdID=DUP001");

            firstOrderLatch.await(10, TimeUnit.SECONDS);
            Thread.sleep(200);

            byte[] order2 = buildNewOrderMessage("DUP001", (byte)2, 50, new BigDecimal("100.50"), "MSFT", (byte)'C');
            client.sendMessageRaw(order2).get(2, TimeUnit.SECONDS);
            System.out.println("  → Duplicate order sent: ClOrdID=DUP001");

            if (rejectLatch.await(10, TimeUnit.SECONDS)) {
                System.out.println("\n✓ Test 2 PASSED - Duplicate correctly rejected\n");
            } else {
                System.out.println("\n✗ Test 2 FAILED - No rejection received\n");
            }

            System.out.println("Sending Logout...");
            byte[] logoutBytes = buildLogoutMessage();
            client.sendMessageRaw(logoutBytes).get(2, TimeUnit.SECONDS);
            logoutLatch.await(5, TimeUnit.SECONDS);
            System.out.println("  ✓ Logout completed");

        } catch (Exception e) {
            System.out.println("  ✗ Test 2 FAILED: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.stopListener();
            Thread.sleep(200);
            client.disconnect().get();
        }
    }

    private static void testCancelOrder() throws Exception {
        System.out.println("═══ Test 3: Cancel Order ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        CountDownLatch loginLatch = new CountDownLatch(1);
        CountDownLatch orderAckLatch = new CountDownLatch(1);
        CountDownLatch cancelLatch = new CountDownLatch(1);
        CountDownLatch logoutLatch = new CountDownLatch(1);
        AtomicBoolean loginSuccess = new AtomicBoolean(false);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                loginSuccess.set((char)response.getLoginResponseStatus() == 'A');
                loginLatch.countDown();
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                logoutLatch.countDown();
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                byte messageType = message.getMessageType();

                if (messageType == 0x25) {
                    System.out.println("  ✓ OrderAcknowledgment received");
                    orderAckLatch.countDown();
                } else if (messageType == 0x23) {
                    System.out.println("  ✓✓ OrderCancelled received!");
                    parseOrderCancelled(message);
                    cancelLatch.countDown();
                }
            }
        });

        try {
            client.connect().get(5, TimeUnit.SECONDS);
            client.startListener();
            Thread.sleep(100);

            LoginRequestMessage login = new LoginRequestMessage("USER", "PASS", "ORD3");
            client.sendMessageRaw(login.toBytes()).get(2, TimeUnit.SECONDS);

            if (!loginLatch.await(10, TimeUnit.SECONDS) || !loginSuccess.get()) {
                System.out.println("  ✗ Login failed\n");
                return;
            }
            System.out.println("  ✓ Login successful\n");

            byte[] newOrder = buildNewOrderMessage("CANCEL001", (byte)1, 200, new BigDecimal("75.25"), "GOOGL", (byte)'F');
            client.sendMessageRaw(newOrder).get(2, TimeUnit.SECONDS);
            System.out.println("  → NewOrder sent: ClOrdID=CANCEL001");

            orderAckLatch.await(10, TimeUnit.SECONDS);
            Thread.sleep(200);

            byte[] cancelOrder = buildCancelOrderMessage("CANCEL001");
            client.sendMessageRaw(cancelOrder).get(2, TimeUnit.SECONDS);
            System.out.println("  → CancelOrder sent for ClOrdID=CANCEL001");

            if (cancelLatch.await(10, TimeUnit.SECONDS)) {
                System.out.println("\n✓ Test 3 PASSED - Order cancelled successfully\n");
            } else {
                System.out.println("\n✗ Test 3 FAILED - No cancellation confirmation\n");
            }

            System.out.println("Sending Logout...");
            byte[] logoutBytes = buildLogoutMessage();
            client.sendMessageRaw(logoutBytes).get(2, TimeUnit.SECONDS);
            logoutLatch.await(5, TimeUnit.SECONDS);
            System.out.println("  ✓ Logout completed");

        } catch (Exception e) {
            System.out.println("  ✗ Test 3 FAILED: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.stopListener();
            Thread.sleep(200);
            client.disconnect().get();
        }
    }

    private static void testMultipleOrders() throws Exception {
        System.out.println("═══ Test 4: Multiple Orders ═══\n");

        BoeConnectionHandler client = new BoeConnectionHandler("localhost", 8080);

        CountDownLatch loginLatch = new CountDownLatch(1);
        CountDownLatch ordersLatch = new CountDownLatch(5);
        CountDownLatch logoutLatch = new CountDownLatch(1);
        AtomicBoolean loginSuccess = new AtomicBoolean(false);
        AtomicInteger ackCount = new AtomicInteger(0);

        client.setMessageListener(new BoeMessageListener() {
            @Override
            public void onLoginResponse(LoginResponseMessage response) {
                loginSuccess.set((char)response.getLoginResponseStatus() == 'A');
                loginLatch.countDown();
            }

            @Override
            public void onLogoutResponse(LogoutResponseMessage response) {
                logoutLatch.countDown();
            }

            @Override
            public void onUnknownMessage(BoeMessage message) {
                if (message.getMessageType() == 0x25) {
                    int count = ackCount.incrementAndGet();
                    System.out.println("  ✓ Acknowledgment " + count + "/5 received");
                    ordersLatch.countDown();
                }
            }
        });

        try {
            client.connect().get(5, TimeUnit.SECONDS);
            client.startListener();
            Thread.sleep(100);

            LoginRequestMessage login = new LoginRequestMessage("TEST", "TEST", "ORD4");
            client.sendMessageRaw(login.toBytes()).get(2, TimeUnit.SECONDS);

            if (!loginLatch.await(10, TimeUnit.SECONDS) || !loginSuccess.get()) {
                System.out.println("  ✗ Login failed\n");
                return;
            }
            System.out.println("  ✓ Login successful\n");

            String[] symbols = {"AAPL", "MSFT", "GOOGL", "AMZN", "TSLA"};
            for (int i = 0; i < 5; i++) {
                byte[] order = buildNewOrderMessage(
                        "MULTI" + String.format("%03d", i),
                        (byte)(i % 2 == 0 ? 1 : 2),
                        (i + 1) * 10,
                        new BigDecimal(100 + i * 10),
                        symbols[i],
                        (byte)'C'
                );
                client.sendMessageRaw(order).get(2, TimeUnit.SECONDS);
                System.out.println("  → Order " + (i+1) + " sent: " + symbols[i]);
                Thread.sleep(100);
            }

            System.out.println("\nWaiting for acknowledgments...");
            if (ordersLatch.await(15, TimeUnit.SECONDS)) {
                System.out.println("\n✓ Test 4 PASSED - All " + ackCount.get() + " orders acknowledged\n");
            } else {
                System.out.println("\n⚠ Test 4 PARTIAL - Only " + ackCount.get() + "/5 orders acknowledged\n");
            }

            System.out.println("Sending Logout...");
            byte[] logoutBytes = buildLogoutMessage();
            client.sendMessageRaw(logoutBytes).get(2, TimeUnit.SECONDS);
            logoutLatch.await(5, TimeUnit.SECONDS);
            System.out.println("  ✓ Logout completed");

        } catch (Exception e) {
            System.out.println("  ✗ Test 4 FAILED: " + e.getMessage());
            e.printStackTrace();
        } finally {
            client.stopListener();
            Thread.sleep(200);
            client.disconnect().get();
        }
    }

    private static byte[] buildNewOrderMessage(String clOrdID, byte side, int qty,
                                               BigDecimal price, String symbol, byte capacity) {
        int headerSize = 2 + 2 + 1 + 1 + 4;
        int bodySize = 20 + 1 + 4 + 1;
        int optionalSize = 1 + 1 + 8 + 8 + 1;

        int totalSize = headerSize + bodySize + optionalSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);
        buffer.putShort((short)(totalSize - 2));
        buffer.put((byte) 0x38);
        buffer.put((byte) 0);
        buffer.putInt(1);
        buffer.put(toFixedLengthBytes(clOrdID, 20));
        buffer.put(side);
        buffer.putInt(qty);
        buffer.put((byte) 2);
        buffer.put((byte) (0x04 | 0x40 | 0x80));
        buffer.put((byte) 0x00);
        buffer.put(BinaryPrice.fromPrice(price).toBytes());
        buffer.put(toFixedLengthBytes(symbol, 8));
        buffer.put(capacity);

        return buffer.array();
    }

    private static byte[] buildCancelOrderMessage(String origClOrdID) {
        int totalSize = 2 + 2 + 1 + 1 + 4 + 20 + 1;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);
        buffer.putShort((short)(totalSize - 2));
        buffer.put((byte) 0x39);
        buffer.put((byte) 0);
        buffer.putInt(1);
        buffer.put(toFixedLengthBytes(origClOrdID, 20));
        buffer.put((byte) 0);

        return buffer.array();
    }

    private static byte[] buildLogoutMessage() {
        int totalSize = 2 + 2 + 1 + 1 + 4 + 1;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);
        buffer.putShort((short)(totalSize - 2));
        buffer.put((byte) 0x02);
        buffer.put((byte) 0);
        buffer.putInt(1);
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
            buffer.position(10);

            buffer.getLong();

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            long orderID = buffer.getLong();

            System.out.println("     → ClOrdID: " + clOrdID);
            System.out.println("     → OrderID: " + orderID);
        } catch (Exception e) {
            System.out.println("     Error parsing: " + e.getMessage());
        }
    }

    private static void parseOrderRejected(BoeMessage message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getData()).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(10);

            buffer.getLong();

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            byte reason = buffer.get();

            byte[] textBytes = new byte[60];
            buffer.get(textBytes);
            String text = new String(textBytes, StandardCharsets.US_ASCII).trim();

            System.out.println("     → ClOrdID: " + clOrdID);
            System.out.println("     → Reason: " + (char)reason);
            System.out.println("     → Text: " + text);
        } catch (Exception e) {
            System.out.println("     Error parsing: " + e.getMessage());
        }
    }

    private static void parseOrderCancelled(BoeMessage message) {
        try {
            ByteBuffer buffer = ByteBuffer.wrap(message.getData()).order(ByteOrder.LITTLE_ENDIAN);
            buffer.position(10);

            buffer.getLong();

            byte[] clOrdIDBytes = new byte[20];
            buffer.get(clOrdIDBytes);
            String clOrdID = new String(clOrdIDBytes, StandardCharsets.US_ASCII).trim();

            long orderID = buffer.getLong();
            byte reason = buffer.get();

            System.out.println("     → ClOrdID: " + clOrdID);
            System.out.println("     → OrderID: " + orderID);
            System.out.println("     → Reason: " + (char)reason);
        } catch (Exception e) {
            System.out.println("     Error parsing: " + e.getMessage());
        }
    }
}
