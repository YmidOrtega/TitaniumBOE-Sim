package com.boe.simulator.server.connection;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.IntFunction;

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
import com.boe.simulator.protocol.message.ModifyOrderMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderExecutedMessage;
import com.boe.simulator.protocol.message.QuoteUpdateRejectedMessage;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(10)
class ClientConnectionHandlerResponsesTest {

    private static final byte SERVER_HEARTBEAT = 0x09;
    private static final byte LOGOUT = 0x08;
    private static final byte ORDER_ACKNOWLEDGMENT = 0x25;
    private static final byte ORDER_MODIFIED = 0x27;
    private static final byte ORDER_EXECUTION = 0x2C;
    private static final byte QUOTE_UPDATE_REJECTED = 0x58;

    private ServerSocket listener;
    private Socket client;
    private DataInputStream in;
    private OutputStream out;
    private OrderManager orderManager;
    private int sequence;

    @BeforeEach
    void setUp() throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));
        orderManager = mock(OrderManager.class);

        client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().build(),
                auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(1_000), orderManager));
        in = new DataInputStream(client.getInputStream());
        out = client.getOutputStream();

        send(new LoginRequestMessage("U1", "pass", "S1").toBytes());
        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, new LoginResponseMessage(read()).getLoginResponseStatus());
        read(); // Replay Complete
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        listener.close();
    }

    @Test
    void aggressorExecutions_followTheOrderAcknowledgment() throws Exception {
        Order order = order("B1");
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenReturn(OrderManager.OrderResponse.acknowledged(order, List.of(execution(order, 4), execution(order, 6))));
        send(newOrder("B1"));

        byte[] ack = expect(ORDER_ACKNOWLEDGMENT);
        byte[] first = expect(ORDER_EXECUTION);
        byte[] second = expect(ORDER_EXECUTION);
        assertEquals(1, seq(ack));
        assertEquals(2, seq(first));
        assertEquals(3, seq(second));
        assertEquals(4, OrderExecutedMessage.fromBytes(first).getLastShares());
        assertEquals(6, OrderExecutedMessage.fromBytes(second).getLastShares());
    }

    @Test
    void executionsOfAModifyThatCrosses_followTheOrderModified() throws Exception {
        Order order = order("B2");
        when(orderManager.processModifyOrder(any(ModifyOrderMessage.class), any(ClientSession.class)))
                .thenReturn(OrderManager.ModifyResponse.modified(order, List.of(execution(order, 1))));
        send(modify("B2", "B1"));

        assertEquals(1, seq(expect(ORDER_MODIFIED)));
        assertEquals(2, seq(expect(ORDER_EXECUTION)));
    }

    @Test
    void quoteUpdate_isRejectedWithNotEnabledForQuotes_andTheSessionStaysOpen() throws Exception {
        for (byte type : new byte[]{0x55, 0x59}) {
            send(quoteUpdate(type, "QU" + type));

            byte[] rejected = expect(QUOTE_UPDATE_REJECTED);
            QuoteUpdateRejectedMessage msg = QuoteUpdateRejectedMessage.fromBytes(rejected);
            assertEquals("QU" + type, msg.getQuoteUpdateID());
            assertEquals('F', msg.getQuoteRejectReason());
            assertEquals(0, rejected[5], "Unsequenced: MatchingUnit 0");
            assertEquals(0, seq(rejected), "Unsequenced: SequenceNumber 0");
        }
    }

    @Test
    void unknownMessageType_isAProtocolViolation() throws Exception {
        send(raw((byte) 0x7F, new byte[0]));
        assertLogout("Unknown message type 0x7F");
    }

    @Test
    void specMessageTheSimulatorDoesNotImplement_isAProtocolViolation() throws Exception {
        send(raw((byte) 0x47, new byte[20]));
        assertLogout("Unsupported message type 0x47 (PURGE_ORDERS)");
    }

    @Test
    void cboeToMemberMessageSentByTheMember_isAProtocolViolation() throws Exception {
        send(raw((byte) 0x25, new byte[40]));
        assertLogout("Cboe-only message type 0x25 (ORDER_ACKNOWLEDGMENT)");
    }

    @Test
    void malformedNewOrder_isAProtocolViolation() throws Exception {
        send(raw((byte) 0x38, new byte[4]));
        assertLogout("Malformed message type 0x38 (NEW_ORDER)");
    }

    private void assertLogout(String text) throws IOException {
        byte[] logout = expect(LOGOUT);
        LogoutResponseMessage msg = new LogoutResponseMessage(logout);
        assertEquals(LogoutResponseMessage.REASON_PROTOCOL_VIOLATION, msg.getLogoutReason());
        assertEquals(text, msg.getLogoutReasonText());
        assertThrows(EOFException.class, this::readSkippingHeartbeats, "The connection is closed after the Logout");
    }

    private IntFunction<byte[]> execution(Order order, int qty) {
        Trade trade = Trade.builder().tradeId(qty).symbol("AAPL").buyOrderId(order.getOrderID()).buyClOrdID(order.getClOrdID())
                .buyUsername("U1").sellOrderId(9).sellClOrdID("S9").sellUsername("U2").quantity(qty)
                .price(new BigDecimal("1.00")).aggressorSide(Side.BUY).build();
        OrderExecutedMessage msg = OrderExecutedMessage.fromTrade(trade, order, true);
        msg.setMatchingUnit((byte) 1);
        return seq -> {
            msg.setSequenceNumber(seq);
            return msg.toBytes();
        };
    }

    private byte[] newOrder(String clOrdID) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(10);
        msg.setSymbol("AAPL");
        msg.setSequenceNumber(++sequence);
        return msg.toBytes();
    }

    // header(10) + ClOrdID(20) + OrigClOrdID(20) + NumberOfBitfields(1) + bitfield(1) + OrderQty(4) + Price(8)
    private byte[] modify(String clOrdID, String origClOrdID) {
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) 62).put((byte) 0x3A).put((byte) 0).putInt(++sequence);
        buf.put(java.util.Arrays.copyOf(clOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put(java.util.Arrays.copyOf(origClOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put((byte) 1).put((byte) 0x0C).putInt(10).putLong(10_000L);
        return buf.array();
    }

    // header(10) + QuoteUpdateID(16) + the rest of the quote block, never read
    private byte[] quoteUpdate(byte type, String quoteUpdateID) {
        ByteBuffer body = ByteBuffer.allocate(16 + 8).order(ByteOrder.LITTLE_ENDIAN);
        body.put(java.util.Arrays.copyOf(quoteUpdateID.getBytes(StandardCharsets.US_ASCII), 16));
        return raw(type, body.array());
    }

    private byte[] raw(byte type, byte[] body) {
        ByteBuffer buf = ByteBuffer.allocate(10 + body.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (8 + body.length)).put(type).put((byte) 0).putInt(++sequence);
        buf.put(body);
        return buf.array();
    }

    private static Order order(String clOrdID) {
        Order order = Order.builder()
                .clOrdID(clOrdID)
                .orderID(1_000)
                .username("U1")
                .side(Side.BUY)
                .orderQty(10)
                .price(new BigDecimal("1.00"))
                .ordType(OrdType.LIMIT)
                .symbol("AAPL")
                .build();
        order.acknowledge();
        return order;
    }

    private byte[] expect(byte type) throws IOException {
        byte[] msg = readSkippingHeartbeats();
        assertEquals(type, msg[4], "Message type");
        return msg;
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

    private static int seq(byte[] msg) {
        return ByteBuffer.wrap(msg, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();
    }
}
