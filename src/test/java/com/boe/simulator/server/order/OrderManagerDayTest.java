package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.CancelOrderMessage;
import com.boe.simulator.protocol.message.ModifyOrderMessage;
import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderRejectedMessage;
import com.boe.simulator.protocol.message.UserModifyRejectedMessage;
import com.boe.simulator.protocol.types.BoeTime;
import com.boe.simulator.protocol.types.TimeInForce;
import com.boe.simulator.server.config.PortAttributes;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import com.boe.simulator.server.session.ClientSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class OrderManagerDayTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private TradeRepository tradeRepository;
    @Mock
    private ClientSession session;

    private OrderManager orderManager;
    private MatchingEngine engine;
    private int price = 1;

    @BeforeEach
    void setUp() {
        engine = new MatchingEngine(orderRepository, tradeRepository);
        orderManager = new OrderManager(orderRepository, new OrderValidator(), engine);
        lenient().when(session.getUsername()).thenReturn("u1");
        lenient().when(session.getSessionSubID()).thenReturn("S1");
        lenient().when(session.isAuthenticated()).thenReturn(true);
    }

    // Appends one optional field; it must be the last one in wire order
    private static byte[] withField(byte[] bytes, int bitfield, int bit, byte[] value) {
        int numberOfBitfields = bytes[35] & 0xFF;
        int fieldsStart = 36 + numberOfBitfields;
        byte[] bitfields = java.util.Arrays.copyOf(java.util.Arrays.copyOfRange(bytes, 36, fieldsStart), Math.max(bitfield, numberOfBitfields));
        bitfields[bitfield - 1] |= (byte) bit;
        ByteBuffer out = ByteBuffer.allocate(36 + bitfields.length + bytes.length - fieldsStart + value.length).order(ByteOrder.LITTLE_ENDIAN);
        out.put(bytes, 0, 35).put((byte) bitfields.length).put(bitfields).put(bytes, fieldsStart, bytes.length - fieldsStart).put(value);
        out.putShort(2, (short) (out.capacity() - 2));
        return out.array();
    }

    private NewOrderMessage order(String clOrdID, int qty, char tif, long expireTime, String clearingFirm) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) '1');
        msg.setOrderQty(qty);
        msg.setSymbol("AAPL");
        msg.setPrice(new BigDecimal(price++));
        msg.setOrdType((byte) '2');
        msg.setCapacity((byte) 'C');
        msg.setTimeInForce((byte) tif);
        if (clearingFirm != null) msg.setClearingFirm(clearingFirm);
        byte[] bytes = msg.toBytes();
        if (expireTime != 0) bytes = withField(bytes, 3, 0x80, ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putLong(expireTime).array());
        return NewOrderMessage.parse(bytes);
    }

    private OrderManager.OrderResponse submit(String clOrdID, char tif) {
        return orderManager.processNewOrder(order(clOrdID, 1, tif, 0, "TEST"), session);
    }

    private static long inSeconds(int seconds) {
        return BoeTime.nowEpochNanos() + TimeUnit.SECONDS.toNanos(seconds);
    }

    @Test
    void gtcAndGtd_areAccepted_andGtdNeedsAFutureExpireTime() {
        assertTrue(submit("C1", '1').isAcknowledged());
        assertTrue(orderManager.processNewOrder(order("D1", 1, '6', inSeconds(60), "TEST"), session).isAcknowledged());

        assertEquals("ExpireTime is required for TimeInForce GTD", orderManager.processNewOrder(order("D2", 1, '6', 0, "TEST"), session).getRejectText());
        assertEquals("ExpireTime must be in the future", orderManager.processNewOrder(order("D3", 1, '6', inSeconds(-1), "TEST"), session).getRejectText());
        assertEquals("ExpireTime is only valid with TimeInForce GTD", orderManager.processNewOrder(order("D4", 1, '0', inSeconds(60), "TEST"), session).getRejectText());
    }

    @Test
    void expiredGtdOrders_areCancelledWithX() {
        OrderManager.OrderResponse gtd = orderManager.processNewOrder(order("D1", 1, '6', inSeconds(60), "TEST"), session);

        assertEquals(0, orderManager.expireOrders(BoeTime.nowEpochNanos()));
        assertEquals(1, orderManager.expireOrders(inSeconds(61)));

        assertEquals(OrderState.CANCELLED, gtd.getOrder().getState());
        assertEquals((byte) 'X', gtd.getOrder().getCancelReason());
        assertTrue(orderManager.findByClOrdID("D1").isEmpty());
    }

    @Test
    void endOfDay_expiresDayOrders_andCarriesGtcAndGtd() {
        Order day = submit("A1", '0').getOrder();
        Order gtc = submit("C1", '1').getOrder();

        assertEquals(1, orderManager.persistingOrdersOf("u1").size());
        orderManager.rollToNextDay();

        assertEquals(OrderState.EXPIRED, day.getState());
        assertTrue(orderManager.findByClOrdID("A1").isEmpty());
        assertTrue(gtc.isCarried());
        assertEquals(java.util.List.of(gtc), orderManager.carriedOrdersOf("u1"));
        assertNotNull(engine.getOrderBook("AAPL").orElseThrow().getBestBid(), "The GTC order stays in the book");
    }

    @Test
    void maximumOrderSizeOfThePort_appliesToBoeOrdersOnly() {
        OrderManager.OrderResponse boe = orderManager.processNewOrder(order("B1", 25_001, '0', 0, "TEST"), session);
        assertEquals(OrderRejectedMessage.REASON_ORDER_SIZE_EXCEEDED, boe.getRejectReason());
        assertEquals("OrderQty exceeds the port Maximum Order Size of 25000", boe.getRejectText());

        assertTrue(orderManager.processNewOrder(order("R1", 25_001, '0', 0, "TEST"), "u1").isAcknowledged(), "REST orders are not on a BOE port");
    }

    @Test
    void allowedClearingFirmsAndDefaults() {
        orderManager.setPortAttributes(PortAttributes.SPEC_DEFAULTS
                .withAllowedClearingFirms(Set.of("GOOD"))
                .withDefaults("ACCT1", "GOOD", null));

        assertEquals("ClearingFirm TEST is not allowed on this port",
                orderManager.processNewOrder(order("F1", 1, '0', 0, "TEST"), session).getRejectText());
        Order defaulted = orderManager.processNewOrder(order("F2", 1, '0', 0, null), session).getOrder();
        assertEquals("GOOD", defaulted.getClearingFirm());
        assertEquals("ACCT1", defaulted.getAccount());
    }

    @Test
    void cancelOnDisconnect_followsThePortAttribute() {
        submit("A1", '0');
        submit("C1", '1');
        orderManager.setPortAttributes(PortAttributes.SPEC_DEFAULTS.withCancelOnDisconnect(PortAttributes.CancelScope.DAY));
        assertEquals(1, orderManager.cancelOnDisconnect("u1"), "Day only");
        assertTrue(orderManager.findByClOrdID("C1").isPresent());

        orderManager.setPortAttributes(PortAttributes.SPEC_DEFAULTS);
        orderManager.setMarketClosed(true);
        assertEquals(0, orderManager.cancelOnDisconnect("u1"), "Not at the end of the day");
        orderManager.setMarketClosed(false);
        assertEquals(1, orderManager.cancelOnDisconnect("u1"), "All, the spec default");
        assertTrue(orderManager.findByClOrdID("C1").isEmpty());
    }

    @Test
    void cancelOnRejectPortAttribute_cancelsTheOriginalUnlessTheModifySaysN() {
        orderManager.setPortAttributes(PortAttributes.SPEC_DEFAULTS.withCancelOnReject(true));
        submit("A1", '0');

        OrderManager.ModifyResponse response = orderManager.processModifyOrder(modify("A2", "A1", 26_000), session);

        assertEquals(UserModifyRejectedMessage.REASON_ORDER_SIZE_EXCEEDED, response.getRejectReason());
        assertTrue(response.cancelledOriginal(), "Cancel on Reject applies when CancelOrigOnReject is not sent");
    }

    @Test
    void massCancelGtcFilterP_preservesGtcAndGtd() {
        submit("A1", '0');
        submit("C1", '1');
        CancelOrderMessage massCancel = new CancelOrderMessage("");
        massCancel.setMassCancelInst("AMNBP");
        massCancel.setSendTime(1L);

        assertEquals(1, orderManager.processCancelOrder(CancelOrderMessage.parse(massCancel.toBytes()), session).getMassCancelCount());
        assertTrue(orderManager.findByClOrdID("C1").isPresent());
    }

    // header(10) + ClOrdID(20) + OrigClOrdID(20) + NumberOfBitfields(1) + bitfield(1) + OrderQty(4) + Price(8)
    private static ModifyOrderMessage modify(String clOrdID, String origClOrdID, int qty) {
        ByteBuffer buf = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) 62).put((byte) 0x3A).put((byte) 0).putInt(1);
        buf.put(java.util.Arrays.copyOf(clOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put(java.util.Arrays.copyOf(origClOrdID.getBytes(StandardCharsets.US_ASCII), 20));
        buf.put((byte) 1).put((byte) 0x0C).putInt(qty).putLong(10_000L);
        return ModifyOrderMessage.parse(buf.array());
    }

    @Test
    void timeInForceWithoutAuctions_isStillRejected() {
        for (char tif : new char[]{'2', '7'}) {
            assertEquals("TimeInForce " + TimeInForce.fromByte((byte) tif) + " is not supported by the simulator",
                    submit("T" + tif, tif).getRejectText());
        }
    }
}
