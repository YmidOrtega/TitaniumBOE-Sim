package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BoeTime;
import com.boe.simulator.server.order.Order;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Order Cancelled — Table 87 (p.132), spec v2.11.90
 *
 * Fixed layout (41 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x2A
 *   [5]   MatchingUnit         1B
 *   [6]   SequenceNumber       4B
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text (NUL-padded)
 *   [38]  CancelReason         1B  Text
 *   [39]  ReservedInternal     1B
 *   [40]  NumberOfReturnBitfields 1B
 *   [41]  ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 *
 * Optional fields: Return Bitfields Per Message (p.187).
 */
public final class OrderCancelledMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x2A;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    static final int FIXED_SIZE = 40; // before NumberOfReturnBitfields

    // Cancel reason codes
    public static final byte REASON_USER_REQUESTED = (byte) 'U';
    public static final byte REASON_NO_LIQUIDITY   = (byte) 'N';
    public static final byte REASON_ORDER_EXPIRED  = (byte) 'X';

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private byte cancelReason;

    private ReturnFields returnFields = new ReturnFields();

    public OrderCancelledMessage() {}

    public static OrderCancelledMessage fromOrder(Order order, byte cancelReason) {
        return fromOrder(order, cancelReason, new ReturnFields());
    }

    public static OrderCancelledMessage fromOrder(Order order, byte cancelReason, ReturnFields returnFields) {
        OrderCancelledMessage msg = new OrderCancelledMessage();
        msg.transactTime = BoeTime.nowEpochNanos();
        msg.clOrdID = order.getClOrdID();
        msg.cancelReason = cancelReason;
        msg.returnFields = returnFields;
        return msg;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + returnFields.encodedSize();

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1);
        buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.put(cancelReason);
        buf.put((byte) 0x00);              // ReservedInternal
        returnFields.writeTo(buf);

        return buf.array();
    }

    public static OrderCancelledMessage fromBytes(byte[] data) {
        if (data == null || data.length < FIXED_SIZE + 1)
            throw new IllegalArgumentException("Invalid OrderCancelled data");

        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderCancelledMessage msg = new OrderCancelledMessage();

        buf.position(10);
        msg.transactTime = buf.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buf.get(clOrdIDBytes);
        msg.clOrdID = stripNul(clOrdIDBytes);

        msg.cancelReason = buf.get();
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

    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    public String getClOrdID() { return clOrdID; }
    public byte getCancelReason() { return cancelReason; }
    public ReturnFields getReturnFields() { return returnFields; }

    @Override
    public String toString() {
        return "OrderCancelled{clOrdID='" + clOrdID + "', reason=" + (char) cancelReason + '}';
    }
}
