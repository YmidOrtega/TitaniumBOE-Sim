package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.Capacity;
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

class ReturnBitfieldsPhase7Test {

    private static Order richOrder() {
        return Order.builder()
                .clOrdID("ORD001")
                .orderID(42L)
                .side(Side.BUY)
                .orderQty(100)
                .price(new BigDecimal("1.50"))
                .ordType(OrdType.LIMIT)
                .symbol("SPX")
                .capacity(Capacity.CUSTOMER)
                .account("ACC123")
                .clearingFirm("ABCD")
                .clearingAccount("AC01")
                .build();
    }

    private static Trade trade() {
        return new Trade(9999L, "SPX", 42L, "ORD001", "user",
                43L, "ORD002", "user2",
                50, new BigDecimal("1.50"), Instant.now(), (byte) 1, "");
    }

    @Test
    void loginRequest_roundTripsReturnBitfieldGroups() {
        ByteBuffer groups = ByteBuffer.wrap(new byte[]{
                0x07, 0x00, (byte) 0x81, 0x25, 0x02, 0x01, 0x40,
                0x07, 0x00, (byte) 0x81, 0x2C, 0x02, 0x01, 0x40
        }).order(ByteOrder.LITTLE_ENDIAN);

        ReturnBitfields returnBitfields = ReturnBitfields.parse(2, groups);
        LoginRequestMessage message = new LoginRequestMessage("user", "pass", "S1", (byte) 1, returnBitfields);

        LoginRequestMessage parsed = LoginRequestMessage.parseFromBytes(message.toBytes());

        assertArrayEquals(new byte[]{0x01, 0x40}, parsed.getReturnBitfields().maskFor((byte) 0x25));
        assertArrayEquals(new byte[]{0x01, 0x40}, parsed.getReturnBitfields().maskFor((byte) 0x2C));
    }

    @Test
    void orderAck_usesDefaultMinimumWhenNoNegotiationExists() {
        OrderAcknowledgmentMessage ack = OrderAcknowledgmentMessage.fromOrder(richOrder(), (byte) 1, 1, ReturnBitfields.empty());

        assertArrayEquals(new byte[]{0x00, 0x41, 0x00, 0x00}, ack.getBitfields());

        OrderAcknowledgmentMessage parsed = OrderAcknowledgmentMessage.fromBytes(ack.toBytes());
        assertEquals("SPX", parsed.getSymbol());
        assertEquals(0, parsed.getOrderQty());
        assertEquals(61, ack.toBytes().length);
    }

    @Test
    void orderAck_appliesNegotiatedBitfields() {
        ReturnBitfields returnBitfields = ReturnBitfields.parse(1, ByteBuffer.wrap(new byte[]{
                0x09, 0x00, (byte) 0x81, 0x25, 0x04, 0x15, 0x41, 0x47, 0x00
        }).order(ByteOrder.LITTLE_ENDIAN));

        OrderAcknowledgmentMessage ack = OrderAcknowledgmentMessage.fromOrder(richOrder(), (byte) 1, 1, returnBitfields);
        OrderAcknowledgmentMessage parsed = OrderAcknowledgmentMessage.fromBytes(ack.toBytes());

        assertArrayEquals(new byte[]{0x15, 0x41, 0x47, 0x00}, ack.getBitfields());
        assertEquals((byte) '1', parsed.getSide());
        assertEquals(100, parsed.getOrderQty());
        assertEquals("SPX", parsed.getSymbol());
    }

    @Test
    void orderExecution_usesDefaultMinimumWhenNoNegotiationExists() {
        OrderExecutedMessage execution = OrderExecutedMessage.fromTrade(trade(), richOrder(), true, ReturnBitfields.empty());
        OrderExecutedMessage parsed = OrderExecutedMessage.fromBytes(execution.toBytes());

        assertArrayEquals(new byte[]{0x00, 0x41, 0x00}, execution.getBitfields());
        assertEquals("SPX", parsed.getSymbol());
        assertEquals(Capacity.CUSTOMER.wireValue(), parsed.getCapacity());
        assertEquals(OrderExecutedMessage.LIQUIDITY_REMOVED, parsed.getBaseLiquidityIndicator());
        assertEquals(82, execution.toBytes().length);
    }

    @Test
    void orderExecution_appliesNegotiatedBitfields() {
        ReturnBitfields returnBitfields = ReturnBitfields.parse(1, ByteBuffer.wrap(new byte[]{
                0x08, 0x00, (byte) 0x81, 0x2C, 0x03, 0x00, 0x41, 0x46
        }).order(ByteOrder.LITTLE_ENDIAN));

        OrderExecutedMessage execution = OrderExecutedMessage.fromTrade(trade(), richOrder(), false, returnBitfields);
        OrderExecutedMessage parsed = OrderExecutedMessage.fromBytes(execution.toBytes());

        assertArrayEquals(new byte[]{0x00, 0x41, 0x46}, execution.getBitfields());
        assertEquals("SPX", parsed.getSymbol());
        assertEquals(Capacity.CUSTOMER.wireValue(), parsed.getCapacity());
        assertEquals(OrderExecutedMessage.LIQUIDITY_ADDED, parsed.getBaseLiquidityIndicator());
    }
}
