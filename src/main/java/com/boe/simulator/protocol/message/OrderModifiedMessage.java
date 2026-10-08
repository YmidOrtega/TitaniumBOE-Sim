package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BinaryPrice;
import com.boe.simulator.protocol.types.BoeTime;
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
 * Optional fields: Return Bitfields Per Message (p.184).
 */
public final class OrderModifiedMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x27;
    private static final int FIXED_SIZE = 47; // before NumberOfReturnBitfields

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private long orderID;
    private ReturnFields returnFields = new ReturnFields();

    public OrderModifiedMessage() {}

    public static OrderModifiedMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber) {
        return fromOrder(order, matchingUnit, sequenceNumber, null, null);
    }

    public static OrderModifiedMessage fromOrder(Order order, byte matchingUnit, int sequenceNumber,
                                                 ReturnBitfields returnBitfields, String origClOrdID) {
        OrderModifiedMessage msg = new OrderModifiedMessage();
        msg.matchingUnit = matchingUnit;
        msg.sequenceNumber = sequenceNumber;
        msg.transactTime = BoeTime.nowEpochNanos();
        msg.clOrdID = order.getClOrdID();
        msg.orderID = order.getOrderID();
        msg.returnFields = OrderReturnFields.forOrder(order)
                .put(ReturnField.ORIG_CL_ORD_ID, origClOrdID)
                .select(returnBitfields, MESSAGE_TYPE);
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

    public static OrderModifiedMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderModifiedMessage msg = new OrderModifiedMessage();
        msg.matchingUnit = buf.get(5);
        msg.sequenceNumber = buf.getInt(6);
        buf.position(10);
        msg.transactTime = buf.getLong();
        byte[] id = new byte[20];
        buf.get(id);
        int end = id.length;
        while (end > 0 && id[end - 1] == 0) end--;
        msg.clOrdID = new String(id, 0, end, StandardCharsets.US_ASCII);
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

    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public String getClOrdID() { return clOrdID; }
    public long getOrderID() { return orderID; }
    public ReturnFields getReturnFields() { return returnFields; }
    public int getLeavesQty() { return returnFields.get(ReturnField.LEAVES_QTY) instanceof Number n ? n.intValue() : 0; }

    @Override
    public String toString() {
        return "OrderModified{clOrdID='" + clOrdID + "', orderID=" + orderID + '}';
    }
}
