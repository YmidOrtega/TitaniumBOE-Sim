package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.OrdType;
import com.boe.simulator.protocol.types.Side;
import com.boe.simulator.server.matching.Trade;
import com.boe.simulator.server.order.Order;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Wire-format compliance tests for Phase 5 order response messages.
 * Verifies message type codes, fixed sizes, SOM bytes, and ReservedInternal placement.
 */
class OrderResponseMessagesPhase5Test {

    // -----------------------------------------------------------------------
    // Shared test fixture
    // -----------------------------------------------------------------------

    private static Order minimalOrder() {
        return Order.builder()
                .clOrdID("ORD001")
                .orderID(42L)
                .side(Side.BUY)
                .orderQty(100)
                .price(new BigDecimal("1.50"))
                .symbol("SPX")
                .build();
    }

    private static Trade minimalTrade() {
        return new Trade(9999L, "SPX", 42L, "ORD001", "user",
                43L, "ORD002", "user2",
                50, new BigDecimal("1.50"), Instant.now(), (byte) 1, "");
    }

    private static ReturnBitfields fullAckBitfields() {
        return ReturnBitfields.parse(1, ByteBuffer.wrap(new byte[]{
                0x09, 0x00, (byte) 0x81, 0x25, 0x04, 0x15, 0x41, 0x40, 0x08
        }).order(ByteOrder.LITTLE_ENDIAN));
    }

    private static ReturnBitfields fullExecutionBitfields() {
        return ReturnBitfields.parse(1, ByteBuffer.wrap(new byte[]{
                0x08, 0x00, (byte) 0x81, 0x2C, 0x03, 0x00, 0x41, 0x40
        }).order(ByteOrder.LITTLE_ENDIAN));
    }

    // -----------------------------------------------------------------------
    // OrderAcknowledgmentMessage — 0x25
    // -----------------------------------------------------------------------

    @Test
    void orderAck_messageTypeByte() {
        assertEquals(0x25, BoeMessageFactory.ORDER_ACKNOWLEDGMENT & 0xFF);
    }

    @Test
    void orderAck_somAndMessageType() {
        byte[] b = OrderAcknowledgmentMessage.fromOrder(minimalOrder(), (byte) 1, 1, fullAckBitfields()).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x25, b[4]);
    }

    @Test
    void orderAck_reservedInternalAtOffset46() {
        byte[] b = OrderAcknowledgmentMessage.fromOrder(minimalOrder(), (byte) 1, 1, fullAckBitfields()).toBytes();
        assertEquals(0x00, b[46], "ReservedInternal must be 0 at offset 46");
    }

    @Test
    void orderAck_wireSize_minimalOrder() {
        // fixed(48) + bitfields(4) + Side(1)+Price(8)+OrdType(1)+Symbol(8)+Capacity(1)+OrderQty(4)+OpenClose(1) = 76
        byte[] b = OrderAcknowledgmentMessage.fromOrder(minimalOrder(), (byte) 1, 1, fullAckBitfields()).toBytes();
        assertEquals(76, b.length);
    }

    @Test
    void orderAck_fromBytesRoundtrip() {
        Order order = minimalOrder();
        byte[] bytes = OrderAcknowledgmentMessage.fromOrder(order, (byte) 1, 1, fullAckBitfields()).toBytes();
        OrderAcknowledgmentMessage parsed = OrderAcknowledgmentMessage.fromBytes(bytes);
        assertEquals("ORD001", parsed.getClOrdID());
        assertEquals(42L, parsed.getOrderID());
        assertEquals((byte) '1', parsed.getSide());   // Side.BUY wire value
        assertEquals(100, parsed.getOrderQty());
    }

    // -----------------------------------------------------------------------
    // OrderRejectedMessage — 0x26
    // -----------------------------------------------------------------------

    @Test
    void orderRejected_messageTypeByte() {
        assertEquals(0x26, BoeMessageFactory.ORDER_REJECTED & 0xFF);
    }

    @Test
    void orderRejected_wireSize() {
        // FIXED_SIZE = 101, no optional fields
        byte[] b = new OrderRejectedMessage("ORD001", OrderRejectedMessage.REASON_INVALID_SYMBOL, "bad symbol").toBytes();
        assertEquals(101, b.length);
    }

    @Test
    void orderRejected_somAndMessageType() {
        byte[] b = new OrderRejectedMessage("ORD001", OrderRejectedMessage.REASON_INVALID_SYMBOL, "").toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x26, b[4]);
    }

    @Test
    void orderRejected_reservedInternalAtOffset99() {
        byte[] b = new OrderRejectedMessage("ORD001", OrderRejectedMessage.REASON_DUPLICATE_CLORDID, "dup").toBytes();
        assertEquals(0x00, b[99], "ReservedInternal must be 0 at offset 99");
    }

    @Test
    void orderRejected_unsequencedHeaderZero() {
        // MatchingUnit=0 at [5], SequenceNumber=0 at [6-9]
        byte[] b = new OrderRejectedMessage("ORD001", OrderRejectedMessage.REASON_UNKNOWN_ERROR, "").toBytes();
        assertEquals(0x00, b[5], "MatchingUnit must be 0 (unsequenced)");
        assertEquals(0x00, b[6]);
        assertEquals(0x00, b[7]);
        assertEquals(0x00, b[8]);
        assertEquals(0x00, b[9], "SequenceNumber must be 0 (unsequenced)");
    }

    @Test
    void orderRejected_fromBytesRoundtrip() {
        byte[] bytes = new OrderRejectedMessage("ORD001", OrderRejectedMessage.REASON_INVALID_PRICE, "bad px").toBytes();
        OrderRejectedMessage parsed = OrderRejectedMessage.fromBytes(bytes);
        assertEquals("ORD001", parsed.getClOrdID());
        assertEquals(OrderRejectedMessage.REASON_INVALID_PRICE, parsed.getOrderRejectReason());
        assertEquals("bad px", parsed.getText());
    }

    // -----------------------------------------------------------------------
    // OrderModifiedMessage — 0x27
    // -----------------------------------------------------------------------

    @Test
    void orderModified_messageTypeByte() {
        assertEquals(0x27, BoeMessageFactory.ORDER_MODIFIED & 0xFF);
    }

    @Test
    void orderModified_somAndMessageType() {
        byte[] b = OrderModifiedMessage.fromOrder(minimalOrder(), (byte) 1, 2).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x27, b[4]);
    }

    @Test
    void orderModified_reservedInternalAtOffset46() {
        byte[] b = OrderModifiedMessage.fromOrder(minimalOrder(), (byte) 1, 2).toBytes();
        assertEquals(0x00, b[46], "ReservedInternal must be 0 at offset 46");
    }

    @Test
    void orderModified_wireSize_minimalOrder() {
        // fixed(48) + bitfields(5) + Price(8)+OrdType(1)+Symbol(8)+LeavesQty(4) = 74
        byte[] b = OrderModifiedMessage.fromOrder(minimalOrder(), (byte) 1, 2).toBytes();
        assertEquals(74, b.length);
    }

    @Test
    void orderModified_leavesQtyPresent() {
        Order order = minimalOrder();
        OrderModifiedMessage msg = OrderModifiedMessage.fromOrder(order, (byte) 1, 2);
        assertEquals(100, msg.getLeavesQty());
    }

    // -----------------------------------------------------------------------
    // OrderRestatedMessage — 0x28
    // -----------------------------------------------------------------------

    @Test
    void orderRestated_messageTypeByte() {
        assertEquals(0x28, BoeMessageFactory.ORDER_RESTATED & 0xFF);
    }

    @Test
    void orderRestated_wireSize() {
        // FIXED_SIZE = 49, no optional
        byte[] b = new OrderRestatedMessage("ORD001", 42L, OrderRestatedMessage.REASON_RELOAD, (byte) 1, 3).toBytes();
        assertEquals(49, b.length);
    }

    @Test
    void orderRestated_somAndMessageType() {
        byte[] b = new OrderRestatedMessage("ORD001", 42L, OrderRestatedMessage.REASON_RELOAD, (byte) 1, 3).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x28, b[4]);
    }

    @Test
    void orderRestated_restatementReasonAtOffset46() {
        byte[] b = new OrderRestatedMessage("ORD001", 42L, OrderRestatedMessage.REASON_WASH, (byte) 1, 3).toBytes();
        assertEquals((byte) 'W', b[46], "RestatementReason at offset 46");
    }

    @Test
    void orderRestated_reservedInternalAtOffset47() {
        byte[] b = new OrderRestatedMessage("ORD001", 42L, OrderRestatedMessage.REASON_RELOAD, (byte) 1, 3).toBytes();
        assertEquals(0x00, b[47], "ReservedInternal must be 0 at offset 47");
    }

    // -----------------------------------------------------------------------
    // UserModifyRejectedMessage — 0x29
    // -----------------------------------------------------------------------

    @Test
    void userModifyRejected_messageTypeByte() {
        assertEquals(0x29, BoeMessageFactory.USER_MODIFY_REJECTED & 0xFF);
    }

    @Test
    void userModifyRejected_wireSize() {
        byte[] b = new UserModifyRejectedMessage("ORD001", UserModifyRejectedMessage.REASON_NOT_FOUND, "not found").toBytes();
        assertEquals(101, b.length);
    }

    @Test
    void userModifyRejected_somAndMessageType() {
        byte[] b = new UserModifyRejectedMessage("ORD001", UserModifyRejectedMessage.REASON_UNKNOWN, "").toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x29, b[4]);
    }

    @Test
    void userModifyRejected_unsequencedHeaderZero() {
        byte[] b = new UserModifyRejectedMessage("ORD001", UserModifyRejectedMessage.REASON_UNKNOWN, "").toBytes();
        assertEquals(0x00, b[5], "MatchingUnit must be 0 (unsequenced)");
        assertEquals(0x00, b[6]);
        assertEquals(0x00, b[9], "SequenceNumber must be 0 (unsequenced)");
    }

    @Test
    void userModifyRejected_reservedInternalAtOffset99() {
        byte[] b = new UserModifyRejectedMessage("ORD001", UserModifyRejectedMessage.REASON_PENDING_FILL, "fill").toBytes();
        assertEquals(0x00, b[99], "ReservedInternal must be 0 at offset 99");
    }

    // -----------------------------------------------------------------------
    // OrderCancelledMessage — 0x2A
    // -----------------------------------------------------------------------

    @Test
    void orderCancelled_messageTypeByte() {
        assertEquals(0x2A, BoeMessageFactory.ORDER_CANCELLED & 0xFF);
    }

    @Test
    void orderCancelled_wireSize() {
        // FIXED_SIZE = 41, no optional
        Order order = minimalOrder();
        byte[] b = OrderCancelledMessage.fromOrder(order, OrderCancelledMessage.REASON_USER_REQUESTED).toBytes();
        assertEquals(41, b.length);
    }

    @Test
    void orderCancelled_somAndMessageType() {
        Order order = minimalOrder();
        byte[] b = OrderCancelledMessage.fromOrder(order, OrderCancelledMessage.REASON_USER_REQUESTED).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x2A, b[4]);
    }

    @Test
    void orderCancelled_cancelReasonAtOffset38() {
        Order order = minimalOrder();
        byte[] b = OrderCancelledMessage.fromOrder(order, OrderCancelledMessage.REASON_IOC_EXPIRED).toBytes();
        assertEquals((byte) 'I', b[38], "CancelReason at offset 38");
    }

    @Test
    void orderCancelled_reservedInternalAtOffset39() {
        Order order = minimalOrder();
        byte[] b = OrderCancelledMessage.fromOrder(order, OrderCancelledMessage.REASON_USER_REQUESTED).toBytes();
        assertEquals(0x00, b[39], "ReservedInternal must be 0 at offset 39");
    }

    @Test
    void orderCancelled_fromBytesRoundtrip() {
        Order order = minimalOrder();
        OrderCancelledMessage orig = OrderCancelledMessage.fromOrder(order, OrderCancelledMessage.REASON_MASS_CANCEL);
        orig.setMatchingUnit((byte) 1);
        orig.setSequenceNumber(7);
        byte[] bytes = orig.toBytes();
        OrderCancelledMessage parsed = OrderCancelledMessage.fromBytes(bytes);
        assertEquals("ORD001", parsed.getClOrdID());
        assertEquals(OrderCancelledMessage.REASON_MASS_CANCEL, parsed.getCancelReason());
    }

    // -----------------------------------------------------------------------
    // CancelRejectedMessage — 0x2B
    // -----------------------------------------------------------------------

    @Test
    void cancelRejected_messageTypeByte() {
        assertEquals(0x2B, BoeMessageFactory.CANCEL_REJECTED & 0xFF);
    }

    @Test
    void cancelRejected_wireSize() {
        byte[] b = new CancelRejectedMessage("ORD001", CancelRejectedMessage.REASON_ORDER_NOT_FOUND, "not found").toBytes();
        assertEquals(101, b.length);
    }

    @Test
    void cancelRejected_somAndMessageType() {
        byte[] b = new CancelRejectedMessage("ORD001", CancelRejectedMessage.REASON_ALREADY_FILLED, "").toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x2B, b[4]);
    }

    @Test
    void cancelRejected_unsequencedHeaderZero() {
        byte[] b = new CancelRejectedMessage("ORD001", CancelRejectedMessage.REASON_UNKNOWN, "").toBytes();
        assertEquals(0x00, b[5], "MatchingUnit must be 0 (unsequenced)");
        assertEquals(0x00, b[6]);
        assertEquals(0x00, b[9], "SequenceNumber must be 0 (unsequenced)");
    }

    @Test
    void cancelRejected_reservedInternalAtOffset99() {
        byte[] b = new CancelRejectedMessage("ORD001", CancelRejectedMessage.REASON_TOO_LATE_TO_CANCEL, "late").toBytes();
        assertEquals(0x00, b[99], "ReservedInternal must be 0 at offset 99");
    }

    // -----------------------------------------------------------------------
    // OrderExecutedMessage — 0x2C
    // -----------------------------------------------------------------------

    @Test
    void orderExecution_messageTypeByte() {
        assertEquals(0x2C, BoeMessageFactory.ORDER_EXECUTION & 0xFF);
    }

    @Test
    void orderExecution_somAndMessageType() {
        byte[] b = OrderExecutedMessage.fromTrade(minimalTrade(), minimalOrder(), true, fullExecutionBitfields()).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x2C, b[4]);
    }

    @Test
    void orderExecution_reservedInternalAtOffset68() {
        byte[] b = OrderExecutedMessage.fromTrade(minimalTrade(), minimalOrder(), true, fullExecutionBitfields()).toBytes();
        assertEquals(0x00, b[68], "ReservedInternal must be 0 at offset 68");
    }

    @Test
    void orderExecution_wireSize_noClearing() {
        // fixed(70) + bitfields(3) + Symbol(8) + Capacity(1) + OrderQty(4) = 86
        byte[] b = OrderExecutedMessage.fromTrade(minimalTrade(), minimalOrder(), true, fullExecutionBitfields()).toBytes();
        assertEquals(86, b.length);
    }

    @Test
    void orderExecution_fromBytesRoundtrip() {
        Trade trade = minimalTrade();
        Order order = minimalOrder();
        OrderExecutedMessage orig = OrderExecutedMessage.fromTrade(trade, order, false, fullExecutionBitfields());
        orig.setMatchingUnit((byte) 1);
        orig.setSequenceNumber(5);
        byte[] bytes = orig.toBytes();
        OrderExecutedMessage parsed = OrderExecutedMessage.fromBytes(bytes);
        assertEquals("ORD001", parsed.getClOrdID());
        assertEquals(9999L, parsed.getExecID());
        assertEquals(50, parsed.getLastShares());
        assertEquals(0, new BigDecimal("1.50").compareTo(parsed.getLastPx()));
        assertEquals(OrderExecutedMessage.LIQUIDITY_ADDED, parsed.getBaseLiquidityIndicator());
    }

    // -----------------------------------------------------------------------
    // TradeCancelOrCorrectMessage — 0x2D
    // -----------------------------------------------------------------------

    @Test
    void tradeCancelOrCorrect_messageTypeByte() {
        assertEquals(0x2D, BoeMessageFactory.TRADE_CANCEL_CORRECT & 0xFF);
    }

    @Test
    void tradeCancelOrCorrect_wireSize() {
        // FIXED_SIZE = 94, no optional
        byte[] b = TradeCancelOrCorrectMessage.cancel(
                "ORD001", 42L, 9999L, (byte) '1', (byte) 'A',
                "FIRM", "ACCT", 50, new BigDecimal("1.50"), Instant.now(),
                (byte) 1, 10).toBytes();
        assertEquals(94, b.length);
    }

    @Test
    void tradeCancelOrCorrect_somAndMessageType() {
        byte[] b = TradeCancelOrCorrectMessage.cancel(
                "ORD001", 42L, 9999L, (byte) '1', (byte) 'A',
                "FIRM", "ACCT", 50, new BigDecimal("1.50"), Instant.now(),
                (byte) 1, 10).toBytes();
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals((byte) 0x2D, b[4]);
    }

    @Test
    void tradeCancelOrCorrect_reservedInternalAtOffset92() {
        byte[] b = TradeCancelOrCorrectMessage.cancel(
                "ORD001", 42L, 9999L, (byte) '1', (byte) 'A',
                "FIRM", "ACCT", 50, new BigDecimal("1.50"), Instant.now(),
                (byte) 1, 10).toBytes();
        assertEquals(0x00, b[92], "ReservedInternal must be 0 at offset 92");
    }

    @Test
    void tradeCancelOrCorrect_cancelHasCorrectedPriceZero() {
        TradeCancelOrCorrectMessage msg = TradeCancelOrCorrectMessage.cancel(
                "ORD001", 42L, 9999L, (byte) '1', (byte) 'A',
                "FIRM", "ACCT", 50, new BigDecimal("1.50"), Instant.now(),
                (byte) 1, 10);
        assertEquals(0, BigDecimal.ZERO.compareTo(msg.getCorrectedPrice()));
    }

    @Test
    void tradeCancelOrCorrect_correctHasNonZeroCorrectedPrice() {
        TradeCancelOrCorrectMessage msg = TradeCancelOrCorrectMessage.correct(
                "ORD001", 42L, 9999L, (byte) '1', (byte) 'A',
                "FIRM", "ACCT", 50, new BigDecimal("1.50"), new BigDecimal("1.75"),
                Instant.now(), (byte) 1, 11);
        assertEquals(0, new BigDecimal("1.75").compareTo(msg.getCorrectedPrice()));
    }

    // -----------------------------------------------------------------------
    // BoeMessageFactory — constants and classification
    // -----------------------------------------------------------------------

    @Test
    void factory_allPhase5ConstantsAreResponses() {
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_ACKNOWLEDGMENT));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_REJECTED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_MODIFIED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_RESTATED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.USER_MODIFY_REJECTED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_CANCELLED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.CANCEL_REJECTED));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.ORDER_EXECUTION));
        assertTrue(BoeMessageFactory.isResponse(BoeMessageFactory.TRADE_CANCEL_CORRECT));
    }

    @Test
    void factory_allPhase5ConstantsAreOrderMessages() {
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_ACKNOWLEDGMENT));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_REJECTED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_MODIFIED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_RESTATED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.USER_MODIFY_REJECTED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_CANCELLED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.CANCEL_REJECTED));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.ORDER_EXECUTION));
        assertTrue(BoeMessageFactory.isOrderMessage(BoeMessageFactory.TRADE_CANCEL_CORRECT));
    }

    @Test
    void factory_messageTypeNames() {
        assertEquals("OrderAcknowledgment", BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_ACKNOWLEDGMENT));
        assertEquals("OrderRejected",       BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_REJECTED));
        assertEquals("OrderModified",       BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_MODIFIED));
        assertEquals("OrderRestated",       BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_RESTATED));
        assertEquals("UserModifyRejected",  BoeMessageFactory.getMessageTypeName(BoeMessageFactory.USER_MODIFY_REJECTED));
        assertEquals("OrderCancelled",      BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_CANCELLED));
        assertEquals("CancelRejected",      BoeMessageFactory.getMessageTypeName(BoeMessageFactory.CANCEL_REJECTED));
        assertEquals("OrderExecution",      BoeMessageFactory.getMessageTypeName(BoeMessageFactory.ORDER_EXECUTION));
        assertEquals("TradeCancelOrCorrect",BoeMessageFactory.getMessageTypeName(BoeMessageFactory.TRADE_CANCEL_CORRECT));
    }
}
