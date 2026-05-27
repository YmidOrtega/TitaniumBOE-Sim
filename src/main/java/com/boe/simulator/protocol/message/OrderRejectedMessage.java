package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Order Rejected — Table 73 (p.119), spec v2.11.90
 * Unsequenced: MatchingUnit=0, SequenceNumber=0.
 *
 * Fixed layout (101 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x26
 *   [5]   MatchingUnit         1B  (0 — unsequenced)
 *   [6]   SequenceNumber       4B  (0 — unsequenced)
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text
 *   [38]  OrderRejectReason    1B  Text
 *   [39]  Text                 60B Text
 *   [99]  ReservedInternal     1B
 *   [100] NumberOfReturnBitfields 1B
 *   [101] ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 */
public final class OrderRejectedMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x26;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 101; // before bitfields/optional

    // Reject reason codes (Order Reason Codes p.213)
    public static final byte REASON_DUPLICATE_CLORDID          = (byte) 'D';
    public static final byte REASON_INVALID_SYMBOL             = (byte) 'S';
    public static final byte REASON_INVALID_PRICE              = (byte) 'P';
    public static final byte REASON_INVALID_QUANTITY           = (byte) 'Q';
    public static final byte REASON_MISSING_REQUIRED_FIELD     = (byte) 'M';
    public static final byte REASON_UNAUTHORIZED               = (byte) 'U';
    public static final byte REASON_UNKNOWN_ERROR              = (byte) 'X';
    public static final byte REASON_INVALID_CAPACITY           = (byte) 'C';
    public static final byte REASON_RATE_LIMIT_EXCEEDED        = (byte) 'R';
    public static final byte REASON_SESSION_NOT_AUTHENTICATED  = (byte) 'A';

    // Header (unsequenced — always 0)
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private long transactTime;
    private String clOrdID;
    private byte orderRejectReason;
    private String text;

    // Bitfields (no optional fields by default)
    private int numberOfBitfields;
    private byte[] bitfields;

    public OrderRejectedMessage() {}

    public OrderRejectedMessage(String clOrdID, byte reason, String text) {
        this.clOrdID = clOrdID;
        this.orderRejectReason = reason;
        this.text = text;
        this.transactTime = System.nanoTime();
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
        this.numberOfBitfields = 0;
        this.bitfields = new byte[0];
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
        buf.put(orderRejectReason);
        putText(buf, text, 60);
        buf.put((byte) 0x00);              // ReservedInternal
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        return buf.array();
    }

    public static OrderRejectedMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        OrderRejectedMessage msg = new OrderRejectedMessage();

        buf.position(10);
        msg.transactTime = buf.getLong();

        byte[] clOrdIDBytes = new byte[20];
        buf.get(clOrdIDBytes);
        msg.clOrdID = stripNul(clOrdIDBytes);

        msg.orderRejectReason = buf.get();

        byte[] textBytes = new byte[60];
        buf.get(textBytes);
        msg.text = stripNul(textBytes);

        if (buf.remaining() >= 2) {
            buf.get(); // ReservedInternal
            msg.numberOfBitfields = buf.get() & 0xFF;
            msg.bitfields = new byte[msg.numberOfBitfields];
            if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);
        } else {
            msg.numberOfBitfields = 0;
            msg.bitfields = new byte[0];
        }

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
    public byte getOrderRejectReason() { return orderRejectReason; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "OrderRejected{clOrdID='" + clOrdID + "', reason=" + (char) orderRejectReason
                + ", text='" + text + "'}";
    }
}
