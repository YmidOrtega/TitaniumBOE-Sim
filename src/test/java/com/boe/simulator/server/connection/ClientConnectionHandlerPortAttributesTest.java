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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.LoginRequestMessage;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderAcknowledgmentMessage;
import com.boe.simulator.protocol.message.OrderCancelledMessage;
import com.boe.simulator.protocol.message.ReturnBitfields;
import com.boe.simulator.protocol.message.ReturnField;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.protocol.types.TimeInForce;
import com.boe.simulator.server.auth.AuthenticationResult;
import com.boe.simulator.server.auth.AuthenticationService;
import com.boe.simulator.server.config.PortAttributes;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.error.ErrorHandler;
import com.boe.simulator.server.order.Order;
import com.boe.simulator.server.order.OrderManager;
import com.boe.simulator.server.ratelimit.RateLimiter;
import com.boe.simulator.server.session.ClientSession;
import com.boe.simulator.server.session.ClientSessionManager;

@Timeout(10)
class ClientConnectionHandlerPortAttributesTest {

    private static final byte SERVER_HEARTBEAT = 0x09;

    private ServerSocket listener;
    private Socket client;
    private DataInputStream in;
    private OutputStream out;
    private OrderManager orderManager;
    private int sequence;

    private void connect(PortAttributes attributes) throws IOException {
        listener = new ServerSocket(0, 1, InetAddress.getLoopbackAddress());
        AuthenticationService auth = mock(AuthenticationService.class);
        when(auth.authenticate(anyString(), anyString(), anyString())).thenReturn(AuthenticationResult.accepted("ok"));
        orderManager = mock(OrderManager.class);
        client = new Socket(InetAddress.getLoopbackAddress(), listener.getLocalPort());
        Socket server = listener.accept();
        Thread.ofVirtual().start(new ClientConnectionHandler(server, 1, ServerConfiguration.builder().portAttributes(attributes).build(),
                auth, new ClientSessionManager(), new ErrorHandler(), new RateLimiter(0), orderManager));
        in = new DataInputStream(client.getInputStream());
        out = client.getOutputStream();
    }

    @AfterEach
    void tearDown() throws IOException {
        if (client != null) client.close();
        if (listener != null) listener.close();
    }

    // Order Acknowledgment: BaseLiquidityIndicator (byte 5, bit 64) and SubLiquidityIndicator (byte 7, bit 1)
    private static ReturnBitfields liquidityIndicators() {
        ByteBuffer buf = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 12).put((byte) 0x81).put((byte) 0x25).put((byte) 7)
                .put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0x40).put((byte) 0).put((byte) 0x01);
        buf.flip();
        return ReturnBitfields.parse(1, buf);
    }

    private static Order gtc(String clOrdID) {
        Order order = Order.builder().clOrdID(clOrdID).orderID(7).username("U1").side(Side.BUY).orderQty(1)
                .price(new BigDecimal("1.00")).ordType(OrdType.LIMIT).timeInForce(TimeInForce.GTC).symbol("AAPL").build();
        order.acknowledge();
        return order;
    }

    @Test
    void restatementsWithoutLiquidityIndicatorsAtLogin_failWithF() throws Exception {
        connect(PortAttributes.SPEC_DEFAULTS.withRestatements(true, false));
        send(new LoginRequestMessage("U1", "pass", "S1").toBytes());

        LoginResponseMessage response = new LoginResponseMessage(read());
        assertEquals(LoginResponseMessage.STATUS_INVALID_BITFIELD, response.getLoginResponseStatus());
    }

    @Test
    void carriedOrders_areRestatedAfterTheLoginResponse() throws Exception {
        connect(PortAttributes.SPEC_DEFAULTS.withRestatements(false, true));
        when(orderManager.carriedOrdersOf("U1")).thenReturn(List.of(gtc("C1")));
        send(new LoginRequestMessage("U1", "pass", "S1", (byte) 0, liquidityIndicators()).toBytes());

        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, new LoginResponseMessage(read()).getLoginResponseStatus());
        OrderAcknowledgmentMessage restated = OrderAcknowledgmentMessage.fromBytes(readSkippingHeartbeats());
        assertEquals("C1", restated.getClOrdID());
        assertEquals(1, restated.getSequenceNumber());
        assertEquals("A", restated.getReturnFields().get(ReturnField.BASE_LIQUIDITY_INDICATOR));
        assertEquals("C", restated.getReturnFields().get(ReturnField.SUB_LIQUIDITY_INDICATOR));
        assertEquals(0x13, readSkippingHeartbeats()[4], "Replay Complete follows the restatements");
    }

    @Test
    void aboveTheOrderRateThreshold_newOrdersAreRejectedWithK() throws Exception {
        connect(PortAttributes.SPEC_DEFAULTS.withOrderRateThresholds(1, 1));
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenReturn(OrderManager.OrderResponse.acknowledged(gtc("N1")));
        login();

        send(newOrder("N1"));
        assertEquals(0x25, readSkippingHeartbeats()[4]);
        send(newOrder("N2"));

        byte[] rejected = readSkippingHeartbeats();
        assertEquals(0x26, rejected[4]);
        assertEquals('K', rejected[38]);
        verify(orderManager, times(1)).processNewOrder(any(NewOrderMessage.class), any(ClientSession.class));
    }

    @Test
    void aboveTheOrderRateThreshold_aModifyIsProcessedAsACancel() throws Exception {
        connect(PortAttributes.SPEC_DEFAULTS.withOrderRateThresholds(1, 1));
        Order order = gtc("A1");
        when(orderManager.processNewOrder(any(NewOrderMessage.class), any(ClientSession.class)))
                .thenReturn(OrderManager.OrderResponse.acknowledged(order));
        when(orderManager.findByClOrdID("A1")).thenReturn(java.util.Optional.of(order));
        when(orderManager.processCancelOrder(any(CancelOrderMessage.class), any(ClientSession.class)))
                .thenReturn(OrderManager.CancelResponse.cancelled(order, OrderCancelledMessage.REASON_USER_REQUESTED));
        login();
        send(newOrder("A1"));
        readSkippingHeartbeats();

        send(modify("A2", "A1"));

        assertEquals(0x2A, readSkippingHeartbeats()[4], "Order Cancelled, not Order Modified");
    }

    @Test
    void disconnect_triggersCancelOnDisconnect() throws Exception {
        connect(PortAttributes.SPEC_DEFAULTS);
        login();

        client.close();

        verify(orderManager, timeout(2_000)).cancelOnDisconnect("U1");
    }

    private void login() throws IOException {
        send(new LoginRequestMessage("U1", "pass", "S1").toBytes());
        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, new LoginResponseMessage(read()).getLoginResponseStatus());
        read(); // Replay Complete
    }

    private byte[] newOrder(String clOrdID) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(1);
        msg.setSymbol("AAPL");
        msg.setSequenceNumber(++sequence);
        return msg.toBytes();
    }

    private byte[] modify(String clOrdID, String origClOrdID) {
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) 62).put((byte) 0x3A).put((byte) 0).putInt(++sequence);
        buf.put(java.util.Arrays.copyOf(clOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put(java.util.Arrays.copyOf(origClOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put((byte) 1).put((byte) 0x0C).putInt(1).putLong(10_000L);
        return buf.array();
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
}
