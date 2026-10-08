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
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.CancelRejectedMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.MassCancelAcknowledgmentMessage;
import com.boe.simulator.protocol.message.OrderCancelledMessage;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.order.OrderManager.CancelResponse;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(10)
class ClientConnectionHandlerCancelTest {

    private static final byte SERVER_HEARTBEAT = 0x09;
    private static final byte ORDER_CANCELLED = 0x2A;
    private static final byte CANCEL_REJECTED = 0x2B;
    private static final byte MASS_CANCEL_ACK = 0x36;

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

    private void respond(CancelResponse response) {
        when(orderManager.processCancelOrder(any(CancelOrderMessage.class), any(ClientSession.class))).thenReturn(response);
    }

    @Test
    void rejectedSingleCancel_sendsCancelRejectedWithTheReason() throws Exception {
        respond(CancelResponse.rejected("ORIG1", CancelRejectedMessage.REASON_ORDER_NOT_FOUND, "Order not found or already terminated"));
        send(cancel("ORIG1"));

        byte[] rejected = readSkippingHeartbeats();
        assertEquals(CANCEL_REJECTED, rejected[4]);
        assertEquals("ORIG1", text(rejected, 18, 20));
        assertEquals('O', rejected[38]);
        assertEquals("Order not found or already terminated", text(rejected, 39, 60));
        assertEquals(0, seq(rejected), "Cancel Rejected is unsequenced");
    }

    @Test
    void singleCancel_sendsOrderCancelled() throws Exception {
        respond(CancelResponse.cancelled(order("ORIG1"), OrderCancelledMessage.REASON_USER_REQUESTED));
        send(cancel("ORIG1"));

        byte[] cancelled = readSkippingHeartbeats();
        assertEquals(ORDER_CANCELLED, cancelled[4]);
        assertEquals("ORIG1", text(cancelled, 18, 20));
        assertEquals(1, seq(cancelled));
    }

    @Test
    void massCancelStyleM_sendsOneOrderCancelledPerOrderAndNoAck() throws Exception {
        respond(CancelResponse.massCancelled(List.of(order("A1"), order("A2")), 'M', null));
        send(massCancel("AM", null));

        assertEquals("A1", text(expect(ORDER_CANCELLED), 18, 20));
        assertEquals("A2", text(expect(ORDER_CANCELLED), 18, 20));
        assertNoFurtherMessage();
    }

    @Test
    void massCancelStyleS_sendsOnlyTheMassCancelAcknowledgment() throws Exception {
        respond(CancelResponse.massCancelled(List.of(order("A1"), order("A2")), 'S', "MC1"));
        send(massCancel("AS", "MC1"));

        byte[] raw = expect(MASS_CANCEL_ACK);
        MassCancelAcknowledgmentMessage ack = MassCancelAcknowledgmentMessage.fromBytes(raw);
        assertEquals("MC1", ack.getMassCancelId());
        assertEquals(2, ack.getCancelledOrderCount());
        assertEquals(0, seq(raw), "Mass Cancel Acknowledgment is unsequenced");
        assertNoFurtherMessage();
    }

    @Test
    void massCancelStyleB_sendsBoth() throws Exception {
        respond(CancelResponse.massCancelled(List.of(order("A1")), 'B', "MC1"));
        send(massCancel("AB", "MC1"));

        assertEquals("A1", text(expect(ORDER_CANCELLED), 18, 20));
        assertEquals(1, MassCancelAcknowledgmentMessage.fromBytes(expect(MASS_CANCEL_ACK)).getCancelledOrderCount());
    }

    @Test
    void eleventhIdenticalMassCancelInOneSecond_isRejectedWithK() throws Exception {
        respond(CancelResponse.massCancelled(List.of(), 'S', "MC1"));
        for (int i = 0; i < 11; i++) send(massCancel("AS", "MC1"));

        for (int i = 0; i < 10; i++) expect(MASS_CANCEL_ACK);
        byte[] rejected = expect(CANCEL_REJECTED);
        assertEquals('K', rejected[38]);
        assertEquals("More than 10 identical mass cancels per second", text(rejected, 39, 60));
        verify(orderManager, times(10)).processCancelOrder(any(CancelOrderMessage.class), any(ClientSession.class));
    }

    @Test
    void massCancelsWithDifferentFilters_areNotIdentical() throws Exception {
        respond(CancelResponse.massCancelled(List.of(), 'S', "MC1"));
        for (int i = 0; i < 10; i++) send(massCancel("AS", "MC1"));
        CancelOrderMessage other = new CancelOrderMessage("");
        other.setRiskRoot("MSFT");
        other.setMassCancelId("MC1");
        other.setMassCancelInst("AS");
        other.setSendTime(1L);
        other.setSequenceNumber(++sequence);
        send(other.toBytes());

        for (int i = 0; i < 11; i++) expect(MASS_CANCEL_ACK);
    }

    private byte[] cancel(String origClOrdID) {
        CancelOrderMessage msg = new CancelOrderMessage(origClOrdID);
        msg.setSendTime(1L);
        msg.setSequenceNumber(++sequence);
        return msg.toBytes();
    }

    private byte[] massCancel(String inst, String massCancelId) {
        CancelOrderMessage msg = new CancelOrderMessage("");
        if (massCancelId != null) msg.setMassCancelId(massCancelId);
        msg.setMassCancelInst(inst);
        msg.setSendTime(1L);
        msg.setSequenceNumber(++sequence);
        return msg.toBytes();
    }

    private static Order order(String clOrdID) {
        return Order.builder()
                .clOrdID(clOrdID)
                .orderID(1_000)
                .username("U1")
                .side(Side.BUY)
                .orderQty(1)
                .price(new BigDecimal("1.00"))
                .ordType(OrdType.LIMIT)
                .symbol("AAPL")
                .build();
    }

    private byte[] expect(byte type) throws IOException {
        byte[] msg = readSkippingHeartbeats();
        assertEquals(type, msg[4], "Message type");
        return msg;
    }

    private void assertNoFurtherMessage() throws IOException {
        client.setSoTimeout(300);
        try {
            byte[] msg = readSkippingHeartbeats();
            fail("Unexpected message 0x" + String.format("%02X", msg[4]));
        } catch (java.net.SocketTimeoutException expected) {
            // nothing else was sent
        }
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

    private static String text(byte[] msg, int offset, int length) {
        return new String(msg, offset, length, StandardCharsets.US_ASCII).trim();
    }
}
