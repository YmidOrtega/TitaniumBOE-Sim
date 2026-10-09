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
import java.util.Map;
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
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderAcknowledgmentMessage;
import com.boe.simulator.protocol.message.ReturnBitfields;
import com.boe.simulator.protocol.message.UnitSequences;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.BoeSessionState;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(20)
class ClientConnectionHandlerReplayTest {

    private static final byte LOGIN_RESPONSE = 0x24;
    private static final byte LOGOUT = 0x08;
    private static final byte REPLAY_COMPLETE = 0x13;
    private static final byte ORDER_ACK = 0x25;
    private static final byte ORDER_REJECTED = 0x26;

    private final AtomicInteger connectionIds = new AtomicInteger();
    private final AtomicInteger orderIds = new AtomicInteger(1_000);

    private ServerSocket listener;
    private AuthenticationService auth;
    private OrderManager orderManager;
    private ClientSessionManager sessionManager;

    @BeforeEach
    void setUp() throws IOException {
        listener = new ServerSocket(0, 10, InetAddress.getLoopbackAddress());
        auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString()))
                .thenReturn(AuthenticationResult.accepted("Login successful"));
        orderManager = mock(OrderManager.class);
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenAnswer(inv -> OrderManager.OrderResponse.acknowledged(orderFor(inv.getArgument(0))));
        sessionManager = new ClientSessionManager();
    }

    @AfterEach
    void tearDown() throws IOException {
        listener.close();
    }

    @Test
    void reconnect_replaysMissedMessagesThenDropsBackwardSequence() throws Exception {
        try (Client c = connect()) {
            c.send(login(null));
            LoginResponseMessage response = c.expectLoginResponse();
            assertEquals(LoginResponseMessage.STATUS_ACCEPTED, response.getLoginResponseStatus());
            assertEquals(0, response.getLastReceivedSequenceNumber());
            assertEquals(Map.of(1, 0), response.getUnitSequences(), "A pair for every unit, even with no messages yet");
            c.expect(REPLAY_COMPLETE);

            c.send(newOrder("A1", 1));
            c.send(newOrder("A2", 2));
            assertSequenced(c.expect(ORDER_ACK), 1);
            assertSequenced(c.expect(ORDER_ACK), 2);
        }

        BoeSessionState state = sessionManager.getSessionStates().latestForUser("U1");
        state.sendSequenced(seq -> OrderAcknowledgmentMessage.fromOrder(
                orderFor(newOrderMessage("OFF1", 9)), BoeSessionState.MATCHING_UNIT, seq).toBytes(), null);

        try (Client c = connect()) {
            c.send(login(UnitSequences.of(false, Map.of(1, 1))));
            LoginResponseMessage response = c.expectLoginResponse();
            assertEquals(2, response.getLastReceivedSequenceNumber(), "Last inbound sequence processed before the disconnect");
            assertEquals(Map.of(1, 3), response.getUnitSequences(), "Highest available outbound sequence");
            assertEquals(1, response.getNumberOfParamGroups(), "Unit Sequences group is echoed");

            assertSequenced(c.expect(ORDER_ACK), 2);
            assertSequenced(c.expect(ORDER_ACK), 3);
            c.expect(REPLAY_COMPLETE);

            c.send(newOrder("A3", 2));
            byte[] logout = c.expect(LOGOUT);
            assertEquals(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, logout[10]);
            assertTrue(c.isClosedByServer(), "Backward sequence drops the connection");
        }
    }

    @Test
    void loginWithoutUnitSequences_replaysEverything_andNoUnspecifiedReplaySkipsIt() throws Exception {
        try (Client c = connect()) {
            c.send(login(null));
            c.expectLoginResponse();
            c.expect(REPLAY_COMPLETE);
            c.send(newOrder("B1", 1));
            c.expect(ORDER_ACK);
        }

        try (Client c = connect()) {
            c.send(login(null));
            c.expectLoginResponse();
            assertSequenced(c.expect(ORDER_ACK), 1);
            c.expect(REPLAY_COMPLETE);
        }

        try (Client c = connect()) {
            c.send(login(UnitSequences.of(true, Map.of())));
            c.expectLoginResponse();
            c.expect(REPLAY_COMPLETE);
        }
    }

    @Test
    void orderSentBeforeReplayComplete_isRejectedWithReasonY() throws Exception {
        when(auth.authenticate(anyString(), anyString(), anyString())).thenAnswer(inv -> {
            Thread.sleep(200);
            return AuthenticationResult.accepted("Login successful");
        });

        try (Client c = connect()) {
            c.send(concat(login(null), newOrder("EARLY", 1)));
            c.expectLoginResponse();
            c.expect(REPLAY_COMPLETE);
            byte[] rejected = c.expect(ORDER_REJECTED);
            assertEquals('y', rejected[38]);
            assertEquals(0, ByteBuffer.wrap(rejected, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), "Order Rejected is unsequenced");
        }
        verify(orderManager, never()).processNewOrder(any(NewOrderMessage.class), any(ClientSession.class));
    }

    @Test
    void sequenceAheadOfServer_isRejectedWithQ() throws Exception {
        try (Client c = connect()) {
            c.send(login(UnitSequences.of(false, Map.of(1, 99))));
            assertEquals(LoginResponseMessage.STATUS_SEQUENCE_AHEAD, c.expectLoginResponse().getLoginResponseStatus());
        }
        verify(auth).endSession("U1");
    }

    @Test
    void unknownUnitWithNonZeroSequence_isRejectedWithI() throws Exception {
        try (Client c = connect()) {
            c.send(login(UnitSequences.of(false, Map.of(2, 5))));
            LoginResponseMessage response = c.expectLoginResponse();
            assertEquals(LoginResponseMessage.STATUS_INVALID_UNIT, response.getLoginResponseStatus());
            assertEquals(0, response.getNumberOfUnits(), "Rejected logins carry no unit pairs");
        }
    }

    @Test
    void rejectedSecondLogin_doesNotReleaseTheActiveSession() throws Exception {
        try (Client first = connect()) {
            first.send(login(null));
            first.expectLoginResponse();
            first.expect(REPLAY_COMPLETE);

            when(auth.authenticate(anyString(), anyString(), anyString()))
                    .thenReturn(AuthenticationResult.sessionInUse("Session already active for this user"));
            try (Client second = connect()) {
                second.send(login(null));
                assertEquals(LoginResponseMessage.STATUS_SESSION_IN_USE, second.expectLoginResponse().getLoginResponseStatus());
                assertTrue(second.isClosedByServer());
            }
            Thread.sleep(200);
            verify(auth, never()).endSession(anyString());
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private Client connect() throws IOException {
        Socket client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        ClientConnectionHandler handler = new ClientConnectionHandler(server, connectionIds.incrementAndGet(),
                ServerConfiguration.builder().build(), auth, sessionManager, new ErrorHandler(),
                new RateLimiter(1_000), orderManager);
        Thread thread = Thread.ofVirtual().start(handler);
        return new Client(client, thread);
    }

    private static byte[] login(UnitSequences units) {
        return new LoginRequestMessage("U1", "pass", "S1", (byte) 0, ReturnBitfields.empty(),
                units != null ? units : UnitSequences.absent()).toBytes();
    }

    private static NewOrderMessage newOrderMessage(String clOrdID, int seq) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setSequenceNumber(seq);
        msg.setSymbol("AAPL");
        msg.setPrice(new BigDecimal("1.00"));
        msg.setOrdType((byte) '2');
        return msg;
    }

    private static byte[] newOrder(String clOrdID, int seq) {
        return newOrderMessage(clOrdID, seq).toBytes();
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

    private static void assertSequenced(byte[] msg, int expectedSeq) {
        assertEquals(BoeSessionState.MATCHING_UNIT, msg[5], "Sequenced application messages carry the matching unit");
        assertEquals(expectedSeq, ByteBuffer.wrap(msg, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt());
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] out = new byte[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length);
        System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private static final class Client implements AutoCloseable {
        private final Socket socket;
        private final Thread handlerThread;
        private final DataInputStream in;
        private final OutputStream out;

        Client(Socket socket, Thread handlerThread) throws IOException {
            this.socket = socket;
            this.handlerThread = handlerThread;
            this.in = new DataInputStream(socket.getInputStream());
            this.out = socket.getOutputStream();
        }

        void send(byte[] bytes) throws IOException {
            out.write(bytes);
            out.flush();
        }

        byte[] read() throws IOException {
            byte[] header = new byte[4];
            in.readFully(header);
            int length = (header[2] & 0xFF) | ((header[3] & 0xFF) << 8);
            byte[] full = new byte[2 + length];
            System.arraycopy(header, 0, full, 0, 4);
            in.readFully(full, 4, length - 2);
            return full;
        }

        byte[] expect(byte type) throws IOException {
            while (true) {
                byte[] msg = read();
                if (msg[4] == 0x09) continue; // Server Heartbeat
                assertEquals(type, msg[4], String.format("Expected message 0x%02X but got 0x%02X", type, msg[4]));
                if (type == LOGIN_RESPONSE || type == LOGOUT || type == REPLAY_COMPLETE) {
                    assertEquals(0, msg[5], "Session messages have MatchingUnit 0");
                    assertEquals(0, ByteBuffer.wrap(msg, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), "Session messages are unsequenced");
                }
                return msg;
            }
        }

        LoginResponseMessage expectLoginResponse() throws IOException {
            return new LoginResponseMessage(expect(LOGIN_RESPONSE));
        }

        boolean isClosedByServer() throws IOException {
            return in.read() == -1;
        }

        @Override
        public void close() throws IOException {
            socket.close();
            try {
                handlerThread.join(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
