package com.boe.simulator.server.connection;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(30)
class ClientConnectionHandlerHeartbeatTest {

    private static final byte LOGIN_RESPONSE = 0x24;
    private static final byte LOGOUT = 0x08;
    private static final byte SERVER_HEARTBEAT = 0x09;
    private static final byte REPLAY_COMPLETE = 0x13;
    private static final byte ORDER_ACK = 0x25;

    private final AtomicInteger orderIds = new AtomicInteger(1_000);
    private ServerSocket listener;
    private Socket client;
    private DataInputStream in;
    private OutputStream out;

    @BeforeEach
    void setUp() throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));
        OrderManager orderManager = mock(OrderManager.class);
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenAnswer(inv -> OrderManager.OrderResponse.acknowledged(orderFor(inv.getArgument(0))));

        client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().build(),
                auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(1_000), orderManager));

        in = new DataInputStream(client.getInputStream());
        out = client.getOutputStream();
        out.write(new LoginRequestMessage("U1", "pass", "S1").toBytes());
        out.flush();
        assertEquals(LOGIN_RESPONSE, read()[4]);
        assertEquals(REPLAY_COMPLETE, read()[4]);
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        listener.close();
    }

    @Test
    void idleSession_receivesUnsequencedServerHeartbeatsAboutEverySecond() throws Exception {
        client.setSoTimeout(2_500);
        long t0 = System.nanoTime();
        byte[] first = read();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(SERVER_HEARTBEAT, first[4]);
        assertEquals(0, first[5]);
        assertEquals(0, ByteBuffer.wrap(first, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
        assertTrue(elapsedMs >= 700 && elapsedMs <= 1_600, "First heartbeat after ~1s of silence, took " + elapsedMs + "ms");

        assertEquals(SERVER_HEARTBEAT, read()[4]);
    }

    @Test
    void outboundTraffic_suppressesServerHeartbeats() throws Exception {
        client.setSoTimeout(1_000);
        for (int i = 1; i <= 8; i++) {
            send(newOrder("T" + i, i));
            assertEquals(ORDER_ACK, read()[4], "No heartbeat while the server keeps sending data");
            Thread.sleep(300);
        }
    }

    @Test
    void inboundOrdersWithoutHeartbeats_keepTheSessionAlive() throws Exception {
        client.setSoTimeout(2_000);
        for (int i = 1; i <= 7; i++) {
            send(newOrder("K" + i, i));
            assertEquals(ORDER_ACK, readSkippingHeartbeats()[4]);
            Thread.sleep(1_000);
        }
    }

    @Test
    void silentClient_isLoggedOutAfterFiveSeconds() throws Exception {
        client.setSoTimeout(8_000);
        long t0 = System.nanoTime();
        byte[] logout = readSkippingHeartbeats();
        long elapsedMs = (System.nanoTime() - t0) / 1_000_000;

        assertEquals(LOGOUT, logout[4]);
        assertEquals(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, logout[10]);
        assertEquals("Heartbeat timeout", new String(logout, 11, 60, StandardCharsets.US_ASCII).trim());
        assertTrue(elapsedMs >= 4_500 && elapsedMs <= 6_500, "Logout after ~5s without inbound data, took " + elapsedMs + "ms");
        assertEquals(-1, in.read(), "Connection is closed after the Logout");
    }

    private void send(byte[] bytes) throws IOException {
        out.write(bytes);
        out.flush();
    }

    private byte[] read() throws IOException {
        byte[] header = new byte[4];
        in.readFully(header);
        int length = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
        byte[] full = new byte[2 + length];
        System.arraycopy(header, 0, full, 0, 4);
        in.readFully(full, 4, length - 2);
        return full;
    }

    private byte[] readSkippingHeartbeats() throws IOException {
        while (true) {
            byte[] msg = read();
            if (msg[4] != SERVER_HEARTBEAT) return msg;
        }
    }

    private static byte[] newOrder(String clOrdID, int seq) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setSequenceNumber(seq);
        msg.setSymbol("AAPL");
        msg.setPrice(new BigDecimal("1.00"));
        msg.setOrdType((byte) '2');
        return msg.toBytes();
    }

    private Order orderFor(NewOrderMessage msg) {
        return Order.builder()
                .clOrdID(msg.getClOrdID())
                .orderID(orderIds.incrementAndGet())
                .username("U1")
                .side(Side.fromByte(msg.getSide()))
                .orderQty(msg.getOrderQty())
                .price(msg.getPrice())
                .ordType(OrdType.LIMIT)
                .symbol(msg.getSymbol())
                .build();
    }
}
