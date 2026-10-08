package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BoeTime;
import com.boe.simulator.server.order.Order;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Order Acknowledgment — Table 66 (p.111), spec v2.11.90
 *
 * Fixed header (48 bytes):
 *   [0]  StartOfMessage    2B
 *   [2]  MessageLength     2B
 *   [4]  MessageType       1B  = 0x25
 *   [5]  MatchingUnit      1B
 *   [6]  SequenceNumber    4B
 *   [10] TransactionTime   8B  DateTime
 *   [18] ClOrdID           20B Text (NUL-padded)
 *   [38] OrderID           8B  Binary
 *   [46] ReservedInternal  1B  (always 0x00)
 *   [47] NumberOfReturnBitfields 1B
 *   [48] ReturnBitfield¹…ᴺ  NB
 *        Optional fields… (Return Bitfields Per Message, p.180)
 */
public final class OrderAcknowledgmentMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x25;
    private static final int FIXED_SIZE = 47; // before NumberOfReturnBitfields

    private byte matchingUnit;
    private int sequenceNumber;
    private long transactTime;
    private String clOrdID;
    private long orderID;
    private ReturnFields returnFields = new ReturnFields();

    public OrderAcknowledgmentMessage() {}

    public static OrderAcknowledgmentMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber) {
        return fromOrder(order, matchingUnit, sequenceNumber, (ReturnBitfields) null);
    }

    public static OrderAcknowledgmentMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber, ReturnBitfields returnBitfields) {
        return fromOrder(order, matchingUnit, sequenceNumber, OrderReturnFields.forOrder(order).select(returnBitfields, MESSAGE_TYPE));
    }

    public static OrderAcknowledgmentMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber, ReturnFields returnFields) {
        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();
        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.transactTime = BoeTime.nowEpochNanos();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.returnFields = returnFields;
        return msg;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + returnFields.encodedSize();
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0xBA).put((byte) 0xBA);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.putLong(orderID);
        buf.put((byte) 0x00);              // ReservedInternal
        returnFields.writeTo(buf);
        return buf.array();
    }

    public static OrderAcknowledgmentMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderAcknowledgmentMessage msg = new OrderAcknowledgmentMessage();
        msg.matchingUnit = buf.get(5);
        msg.sequenceNumber = buf.getInt(6);
        buf.position(10);
        msg.transactTime = buf.getLong();
        byte[] clOrdIDBytes = new byte[20];
        buf.get(clOrdIDBytes);
        msg.clOrdID = stripNul(clOrdIDBytes);
        msg.orderID = buf.getLong();
        buf.get(); // ReservedInternal
        msg.returnFields = ReturnFields.readFrom(buf);
        return msg;
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    private static String stripNul(byte[] b) {
        int end = b.length;
        while (end > 0 && b[end - 1] == 0) end--;
        return new String(b, 0, end, StandardCharsets.US_ASCII);
    }

    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public ReturnFields getReturnFields() { return returnFields; }
    public byte getSide() { return firstChar(returnFields.get(ReturnField.SIDE)); }
    public BigDecimal getPrice() { return (BigDecimal) returnFields.get(ReturnField.PRICE); }
    public String getSymbol() { return (String) returnFields.get(ReturnField.SYMBOL); }
    public int getOrderQty() { return returnFields.get(ReturnField.ORDER_QTY) instanceof Number n ? n.intValue() : 0; }
    public byte[] getBitfields() { return returnFields.mask(); }

    static byte firstChar(Object value) {
        if (value instanceof Byte b) return b;
        return value instanceof String s && !s.isEmpty() ? (byte) s.charAt(0) : 0;
    }

    @Override
    public String toString() {
        return "OrderAcknowledgment{clOrdID='" + clOrdID + "', orderID=" + orderID + '}';
    }
}
