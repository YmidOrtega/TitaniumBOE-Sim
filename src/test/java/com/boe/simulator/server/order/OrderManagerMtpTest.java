package com.boe.simulator.server.order;

import com.boe.simulator.protocol.message.NewOrderMessage;
import com.boe.simulator.protocol.message.OrderRejectedMessage;
import com.boe.simulator.server.matching.MatchingEngine;
import com.boe.simulator.server.matching.TradeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class OrderManagerMtpTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private TradeRepository tradeRepository;

    private OrderManager orderManager;

    @BeforeEach
    void setUp() {
        orderManager = new OrderManager(orderRepository, new OrderValidator(), new MatchingEngine(orderRepository, tradeRepository));
    }

    private static NewOrderMessage order(String clOrdID, char side, String preventMatch) {
        NewOrderMessage msg = new NewOrderMessage();
        msg.setClOrdID(clOrdID);
        msg.setSide((byte) side);
        msg.setOrderQty(5);
        msg.setSymbol("AAPL");
        msg.setPrice(new BigDecimal("10.00"));
        msg.setOrdType((byte) '2');
        msg.setCapacity((byte) 'C');
        byte[] bytes = msg.toBytes();
        if (preventMatch == null) return NewOrderMessage.parse(bytes);
        return NewOrderMessage.parse(withPreventMatch(bytes, preventMatch));
    }

    // Appends PreventMatch (bitfield 3, bit 32) to an encoded New Order
    private static byte[] withPreventMatch(byte[] bytes, String preventMatch) {
        ByteBuffer in = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int numberOfBitfields = bytes[35] & 0xFF;
        int fieldsStart = 36 + numberOfBitfields;
        byte[] bitfields = java.util.Arrays.copyOfRange(bytes, 36, fieldsStart);
        byte[] newBitfields = java.util.Arrays.copyOf(bitfields, Math.max(3, bitfields.length));
        newBitfields[2] |= 0x20;
        ByteBuffer out = ByteBuffer.allocate(36 + newBitfields.length + bytes.length - fieldsStart + 3).order(ByteOrder.LITTLE_ENDIAN);
        out.put(bytes, 0, 35).put((byte) newBitfields.length).put(newBitfields);
        // Byte 1 and 2 fields stay in front; Bitfield 3 has nothing before PreventMatch here
        out.put(bytes, fieldsStart, bytes.length - fieldsStart);
        out.put(java.util.Arrays.copyOf(preventMatch.getBytes(StandardCharsets.US_ASCII), 3));
        out.putShort(2, (short) (out.capacity() - 2));
        return out.array();
    }

    @Test
    void restingOrderCancelledByMtp_leavesTheActiveOrders() {
        assertTrue(orderManager.processNewOrder(order("S1", '2', null), "u1").isAcknowledged());

        OrderManager.OrderResponse buy = orderManager.processNewOrder(order("B1", '1', null), "u1");

        assertTrue(buy.isAcknowledged());
        assertTrue(orderManager.findByClOrdID("S1").isEmpty(), "The cancelled resting order must not stay live");
        assertEquals(1, orderManager.getTotalOrdersCancelled());
        assertTrue(orderManager.findByClOrdID("B1").isPresent(), "The inbound order rests");
    }

    @Test
    void inboundOrderCancelledByMtp_isAcknowledgedWithReasonV() {
        orderManager.processNewOrder(order("S1", '2', null), "u1");

        OrderManager.OrderResponse buy = orderManager.processNewOrder(order("B1", '1', "NF"), "u1");

        assertEquals(OrderState.CANCELLED, buy.getOrder().getState());
        assertEquals((byte) 'V', buy.getOrder().getCancelReason());
        assertTrue(orderManager.findByClOrdID("B1").isEmpty());
        assertTrue(orderManager.findByClOrdID("S1").isPresent());
    }

    @Test
    void invalidPreventMatch_isRejectedWithZ() {
        OrderManager.OrderResponse response = orderManager.processNewOrder(order("B1", '1', "XF"), "u1");

        assertEquals(OrderRejectedMessage.REASON_UNFORESEEN, response.getRejectReason());
        assertEquals("Invalid PreventMatch MTP Modifier 'X'", response.getRejectText());
    }
}
