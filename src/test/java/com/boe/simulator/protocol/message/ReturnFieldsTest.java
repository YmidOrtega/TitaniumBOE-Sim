package com.boe.simulator.protocol.message;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.boe.simulator.protocol.types.Capacity;
import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.order.Order;

class ReturnFieldsTest {

    private static final byte[] ALL_MESSAGES = {0x25, 0x26, 0x27, 0x28, 0x29, 0x2A, 0x2B, 0x2C, 0x2D, 0x43, 0x44, 0x46, 0x48};

    private static ReturnBitfields negotiated(byte messageType, int... mask) {
        ByteBuffer buf = ByteBuffer.allocate(5 + mask.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) (5 + mask.length)).put((byte) 0x81).put(messageType).put((byte) mask.length);
        for (int b : mask) buf.put((byte) b);
        buf.flip();
        return ReturnBitfields.parse(1, buf);
    }

    private static Order order(Map<String, byte[]> echo) {
        Order order = Order.builder()
                .clOrdID("ORD1").orderID(77).side(Side.SELL).orderQty(10)
                .price(new BigDecimal("1.25")).ordType(OrdType.LIMIT).symbol("AAPL")
                .capacity(Capacity.CUSTOMER).clearingFirm("TEST").echoFields(echo).build();
        order.acknowledge();
        return order;
    }

    @Test
    void everyBitTheSpecAllowsHasAFieldInTheCatalog() {
        for (byte type : ALL_MESSAGES) {
            byte[] allowed = ReturnBitfieldRules.allowedFor(type);
            for (int i = 0; i < allowed.length; i++) {
                for (int bit = 1; bit <= 0x80; bit <<= 1) {
                    if ((allowed[i] & bit) == 0) continue;
                    int byteNumber = i + 1, b = bit;
                    assertTrue(java.util.Arrays.stream(ReturnField.values()).anyMatch(f -> f.byteNumber() == byteNumber && f.bit() == b),
                            String.format("0x%02X byte %d bit %d", type, byteNumber, bit));
                }
            }
        }
    }

    @Test
    void requestedFieldsGoInByteThenBitOrder_andMissingOnesAreBinaryZero() {
        // Byte 1: Side, Price; byte 3: OrderQty; byte 5: LeavesQty, LastShares (no value on an ack)
        ReturnFields fields = OrderReturnFields.forOrder(order(Map.of()))
                .select(negotiated((byte) 0x25, 0x05, 0x00, 0x40, 0x00, 0x06), (byte) 0x25);

        ByteBuffer buf = ByteBuffer.allocate(fields.encodedSize()).order(ByteOrder.LITTLE_ENDIAN);
        fields.writeTo(buf);
        buf.flip();

        assertEquals(5, buf.get(), "NumberOfReturnBitfields");
        buf.position(6);
        assertEquals('2', buf.get(), "Side");
        assertEquals(12_500L, buf.getLong(), "Price");
        assertEquals(10, buf.getInt(), "OrderQty");
        assertEquals(10, buf.getInt(), "LeavesQty");
        assertEquals(0, buf.getInt(), "LastShares was requested but has no value: binary zero");
        assertFalse(buf.hasRemaining());
    }

    @Test
    void bitsTheMessageDoesNotAllow_areNotReturned() {
        // User Modify Rejected only allows bitfield 10 fields; Side (byte 1) is masked out
        ReturnFields fields = new ReturnFields().put(ReturnField.SIDE, (byte) '1')
                .select(negotiated((byte) 0x29, 0x01), (byte) 0x29);

        assertArrayEquals(new byte[]{0x00}, fields.mask());
        assertEquals(2, fields.encodedSize());
    }

    @Test
    void inboundInformationalFields_areEchoedBack() {
        byte[] echo = "hello".getBytes(StandardCharsets.US_ASCII);
        byte[] cmta = {0x27, 0x02, 0x00, 0x00};
        Order order = order(Map.of("EchoText", echo, "CMTANumber", cmta));

        OrderAcknowledgmentMessage ack = OrderAcknowledgmentMessage.fromOrder(order, (byte) 1, 1,
                negotiated((byte) 0x25, 0, 0, 0, 0, 0, 0, 0, 0x02, 0x20));
        ReturnFields parsed = OrderAcknowledgmentMessage.fromBytes(ack.toBytes()).getReturnFields();

        assertEquals("hello", parsed.get(ReturnField.ECHO_TEXT));
        assertEquals(551L, parsed.get(ReturnField.CMTA_NUMBER));
    }

    @Test
    void orderModified_returnsOrigClOrdIDAndLeavesQty_whenRequested() {
        OrderModifiedMessage msg = OrderModifiedMessage.fromOrder(order(Map.of()), (byte) 1, 3,
                negotiated((byte) 0x27, 0, 0, 0, 0, 0x03), "OLD1");
        OrderModifiedMessage parsed = OrderModifiedMessage.fromBytes(msg.toBytes());

        assertEquals("OLD1", parsed.getReturnFields().get(ReturnField.ORIG_CL_ORD_ID));
        assertEquals(10, parsed.getLeavesQty());
    }

    @Test
    void orderCancelled_returnsSubreason_whenRequested() {
        ReturnFields fields = OrderReturnFields.forOrder(order(Map.of()))
                .put(ReturnField.SUBREASON, (byte) 'B')
                .select(negotiated((byte) 0x2A, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x04), (byte) 0x2A);
        OrderCancelledMessage parsed = OrderCancelledMessage.fromBytes(
                OrderCancelledMessage.fromOrder(order(Map.of()), OrderCancelledMessage.REASON_USER_REQUESTED, fields).toBytes());

        assertEquals("B", parsed.getReturnFields().get(ReturnField.SUBREASON));
    }

    @Test
    void cancelRejected_returnsMassCancelId_whichOrderCancelledDoesNotAllow() {
        ReturnFields values = new ReturnFields().put(ReturnField.MASS_CANCEL_ID, "MC1");
        int[] byte15 = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0x08};

        assertTrue(new ReturnFields().put(ReturnField.MASS_CANCEL_ID, "MC1")
                .select(negotiated((byte) 0x2B, byte15), (byte) 0x2B).isRequested(ReturnField.MASS_CANCEL_ID));
        assertFalse(values.select(negotiated((byte) 0x2A, byte15), (byte) 0x2A).isRequested(ReturnField.MASS_CANCEL_ID));
    }

    @Test
    void orderRejected_echoesTheFieldsOfTheRejectedNewOrder() {
        NewOrderMessage newOrder = new NewOrderMessage();
        newOrder.setClOrdID("R1");
        newOrder.setSide((byte) '1');
        newOrder.setOrderQty(5);
        newOrder.setSymbol("ZZZZ");
        NewOrderMessage parsedOrder = NewOrderMessage.parse(newOrder.toBytes());

        OrderRejectedMessage rejected = new OrderRejectedMessage("R1", OrderRejectedMessage.REASON_SYMBOL_NOT_SUPPORTED, "")
                .withReturnFields(OrderReturnFields.forNewOrder(parsedOrder).select(negotiated((byte) 0x26, 0x01, 0x01, 0x40), (byte) 0x26));
        ReturnFields parsed = OrderRejectedMessage.fromBytes(rejected.toBytes()).getReturnFields();

        assertEquals("1", parsed.get(ReturnField.SIDE));
        assertEquals("ZZZZ", parsed.get(ReturnField.SYMBOL));
        assertEquals(5L, parsed.get(ReturnField.ORDER_QTY));
    }
}
