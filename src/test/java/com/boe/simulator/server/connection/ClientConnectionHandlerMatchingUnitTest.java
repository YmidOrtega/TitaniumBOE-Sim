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

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.ClientHeartbeatMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
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

@Timeout(10)
class ClientConnectionHandlerMatchingUnitTest {

    private static final byte SERVER_HEARTBEAT = 0x09;
    private static final byte ORDER_ACK = 0x25;
    private static final byte ORDER_REJECTED = 0x26;
    private static final byte USER_MODIFY_REJECTED = 0x29;
    private static final byte CANCEL_REJECTED = 0x2B;
    private static final String TEXT = "MatchingUnit must be 0 for inbound messages";

    private ServerSocket listener;
    private Socket client;
    private DataInputStream in;
    private OutputStream out;
    private OrderManager orderManager;

    @BeforeEach
    void setUp() throws Exception {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));
        orderManager = mock(OrderManager.class);
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenAnswer(inv -> OrderManager.OrderResponse.acknowledged(orderFor(inv.getArgument(0))));

        client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().build(),
                auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(1_000), orderManager));
        in = new DataInputStream(client.getInputStream());
        out = client.getOutputStream();
    }

    @AfterEach
    void tearDown() throws IOException {
        client.close();
        listener.close();
    }

    @Test
    void loginWithNonZeroMatchingUnit_isRejectedWithM() throws Exception {
        send(new LoginRequestMessage("U1", "pass", "S1", (byte) 1).toBytes());

        LoginResponseMessage response = new LoginResponseMessage(read());
        assertEquals(LoginResponseMessage.STATUS_INVALID_STRUCTURE, response.getLoginResponseStatus());
        assertEquals("MatchingUnit and SequenceNumber must be 0 in a Login Request", response.getLoginResponseText());
        assertEquals(-1, in.read());
    }

    @Test
    void loginWithNonZeroSequenceNumber_isRejectedWithM() throws Exception {
        LoginRequestMessage login = new LoginRequestMessage("U1", "pass", "S1");
        login.setSequenceNumber(7);
        send(login.toBytes());

        assertEquals(LoginResponseMessage.STATUS_INVALID_STRUCTURE, new LoginResponseMessage(read()).getLoginResponseStatus());
    }

    @Test
    void newOrderWithNonZeroMatchingUnit_isRejectedWithZ_andSessionContinues() throws Exception {
        login();
        NewOrderMessage order = newOrder("MU1", 1);
        order.setMatchingUnit((byte) 1);
        send(order.toBytes());

        byte[] rejected = readSkippingHeartbeats();
        assertEquals(ORDER_REJECTED, rejected[4]);
        assertEquals('Z', rejected[38]);
        assertEquals(TEXT, text(rejected));
        verify(orderManager, never()).processNewOrder(any(NewOrderMessage.class), any(ClientSession.class));

        send(newOrder("OK1", 2).toBytes());
        assertEquals(ORDER_ACK, readSkippingHeartbeats()[4], "The session keeps working after the reject");
    }

    @Test
    void modifyWithNonZeroMatchingUnit_isRejectedWithZ() throws Exception {
        login();
        send(modify("MOD1", "ORIG1", (byte) 1, 1));

        byte[] rejected = readSkippingHeartbeats();
        assertEquals(USER_MODIFY_REJECTED, rejected[4]);
        assertEquals('Z', rejected[38]);
        assertEquals(TEXT, text(rejected));
    }

    @Test
    void cancelWithNonZeroMatchingUnit_isRejectedWithZ() throws Exception {
        login();
        CancelOrderMessage cancel = new CancelOrderMessage("ORIG1");
        cancel.setMatchingUnit((byte) 1);
        cancel.setSequenceNumber(1);
        send(cancel.toBytes());

        byte[] rejected = readSkippingHeartbeats();
        assertEquals(CANCEL_REJECTED, rejected[4]);
        assertEquals("ORIG1", new String(rejected, 18, 20, StandardCharsets.US_ASCII).trim());
        assertEquals('Z', rejected[38]);
        assertEquals(TEXT, text(rejected));
        assertEquals(0, ByteBuffer.wrap(rejected, 6, 4).order(ByteOrder.LITTLE_ENDIAN).getInt(), "Cancel Rejected is unsequenced");
    }

    @Test
    void heartbeatWithNonZeroHeader_isOnlyLogged() throws Exception {
        login();
        send(new ClientHeartbeatMessage((byte) 1, 9).toBytes());
        send(newOrder("AFTERHB", 1).toBytes());

        assertEquals(ORDER_ACK, readSkippingHeartbeats()[4], "No reject and no logout for the heartbeat");
    }

    private void login() throws IOException {
        send(new LoginRequestMessage("U1", "pass", "S1").toBytes());
        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, new LoginResponseMessage(read()).getLoginResponseStatus());
        read(); // Replay Complete
    }

    private static NewOrderMessage newOrder(String clOrdID, int seq) {
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

    // ModifyOrderMessage is inbound-only: header(10) + ClOrdID(20) + OrigClOrdID(20) + NumberOfBitfields(1)
    private static byte[] modify(String clOrdID, String origClOrdID, byte matchingUnit, int seq) {
        ByteBuffer buf = ByteBuffer.allocate(51).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) 49).put((byte) 0x3A).put(matchingUnit).putInt(seq);
        buf.put(java.util.Arrays.copyOf(clOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put(java.util.Arrays.copyOf(origClOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put((byte) 0);
        return buf.array();
    }

    private Order orderFor(NewOrderMessage msg) {
        return Order.builder()
                .clOrdID(msg.getClOrdID())
                .orderID(1_000)
                .username("U1")
                .side(Side.fromByte(msg.getSide()))
                .orderQty(msg.getOrderQty())
                .price(msg.getPrice())
                .ordType(OrdType.LIMIT)
                .symbol(msg.getSymbol())
                .build();
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

    // Reject layouts: TransactTime(8) at 10, ClOrdID(20) at 18, Reason(1) at 38, Text(60) at 39
    private static String text(byte[] reject) {
        return new String(reject, 39, 60, StandardCharsets.US_ASCII).trim();
    }
}
