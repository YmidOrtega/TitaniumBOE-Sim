package com.boe.simulator.protocol.message;

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
 * Relevant optional fields (p.187):
 *   Byte 1: 0x01=Side, 0x04=Price, 0x10=OrdType
 *   Byte 5: 0x01=OrigClOrdID (20B Text)
 */
public final class OrderCancelledMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x2A;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 41;

    // Cancel reason codes
    public static final byte REASON_USER_REQUESTED = (byte) 'U';
    public static final byte REASON_MASS_CANCEL    = (byte) 'M';
    public static final byte REASON_TIMEOUT        = (byte) 'T';
    public static final byte REASON_SUPERVISOR     = (byte) 'S';
    public static final byte REASON_IOC_EXPIRED    = (byte) 'I';

    private byte matchingUnit;
    private int sequenceNumber;

    private long transactTime;
    private String clOrdID;
    private byte cancelReason;

    private int numberOfBitfields;
    private byte[] bitfields;

    public OrderCancelledMessage() {}

    public static OrderCancelledMessage fromOrder(Order order, byte cancelReason) {
        OrderCancelledMessage msg = new OrderCancelledMessage();
        msg.transactTime = System.nanoTime();
        msg.clOrdID = order.getClOrdID();
        msg.cancelReason = cancelReason;
        msg.numberOfBitfields = 0;
        msg.bitfields = new byte[0];
        return msg;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + numberOfBitfields;

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
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        return buf.array();
    }

    public static OrderCancelledMessage fromBytes(byte[] data) {
        if (data == null || data.length < FIXED_SIZE)
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

        msg.numberOfBitfields = buf.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);

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

    @Override
    public String toString() {
        return "OrderCancelled{clOrdID='" + clOrdID + "', reason=" + (char) cancelReason + '}';
    }
}
