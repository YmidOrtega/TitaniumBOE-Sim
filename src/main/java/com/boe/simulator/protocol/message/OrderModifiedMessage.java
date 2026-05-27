package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Order Modified — Table 79 (p.124), spec v2.11.90
 * Sent in response to a successful Modify Order request.
 *
 * Fixed layout (48 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x27
 *   [5]   MatchingUnit         1B
 *   [6]   SequenceNumber       4B
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text (from Modify Order request)
 *   [38]  OrderID              8B  Binary
 *   [46]  ReservedInternal     1B
 *   [47]  NumberOfReturnBitfields 1B
 *   [48]  ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 *
 * Relevant optional fields (p.184 — same bitfield map as Order Ack):
 *   Byte 1: 0x04=Price, 0x10=OrdType
 *   Byte 2: 0x01=Symbol, 0x40=Capacity
 *   Byte 5: 0x02=LeavesQty
 */
public final class OrderModifiedMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x27;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 48;

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private long orderID;

    private int numberOfBitfields;
    private byte[] bitfields;

    // Optional fields
    private BigDecimal price;
    private byte ordType;
    private String symbol;
    private byte capacity;
    private int leavesQty;

    public OrderModifiedMessage() {}

    public static OrderModifiedMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber) {
        OrderModifiedMessage msg = new OrderModifiedMessage();
        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();

        msg.price = order.getPrice();
        msg.ordType = order.getOrdType().wireValue();
        msg.symbol = order.getSymbol();
        msg.capacity = order.getCapacity() != null ? order.getCapacity().wireValue() : 0;
        msg.leavesQty = order.getLeavesQty();

        msg.setupBitfields();
        return msg;
    }

    private void setupBitfields() {
        // 5 bitfield bytes to reach LeavesQty at byte 5
        numberOfBitfields = 5;
        bitfields = new byte[5];

        if (price != null) bitfields[0] |= 0x04;           // Byte 1: Price
        bitfields[0] |= 0x10;                               // Byte 1: OrdType
        if (symbol != null && !symbol.isBlank()) bitfields[1] |= 0x01; // Byte 2: Symbol
        if (capacity != 0) bitfields[1] |= 0x40;           // Byte 2: Capacity
        bitfields[4] |= 0x02;                               // Byte 5: LeavesQty
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int optSize = optionalSize();
        int totalSize = FIXED_SIZE + numberOfBitfields + optSize;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1); buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.putLong(orderID);
        buf.put((byte) 0x00);
        buf.put((byte) numberOfBitfields);
        buf.put(bitfields, 0, numberOfBitfields);

        if ((bitfields[0] & 0x04) != 0) BinaryPrice.fromPrice(price).putInto(buf);
        if ((bitfields[0] & 0x10) != 0) buf.put(ordType);
        if ((bitfields[1] & 0x01) != 0) putAlpha(buf, symbol, 8);
        if ((bitfields[1] & 0x40) != 0) buf.put(capacity);
        if ((bitfields[4] & 0x02) != 0) buf.putInt(leavesQty);

        return buf.array();
    }

    private int optionalSize() {
        int size = 0;
        if ((bitfields[0] & 0x04) != 0) size += 8;
        if ((bitfields[0] & 0x10) != 0) size += 1;
        if ((bitfields[1] & 0x01) != 0) size += 8;
        if ((bitfields[1] & 0x40) != 0) size += 1;
        if ((bitfields[4] & 0x02) != 0) size += 4;
        return size;
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    private static void putAlpha(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        java.util.Arrays.fill(bytes, (byte) 0x20);
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public int getLeavesQty() { return leavesQty; }

    @Override
    public String toString() {
        return "OrderModified{clOrdID='" + clOrdID + "', orderID=" + orderID
                + ", symbol='" + symbol + "', price=" + price + ", leavesQty=" + leavesQty + '}';
    }
}
