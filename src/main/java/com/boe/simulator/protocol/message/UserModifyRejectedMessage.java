package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * User Modify Rejected — Table 85 (p.130), spec v2.11.90
 * Unsequenced: MatchingUnit=0, SequenceNumber=0.
 *
 * Fixed layout (101 bytes minimum):
 *   [0]   StartOfMessage       2B
 *   [2]   MessageLength        2B
 *   [4]   MessageType          1B  = 0x29
 *   [5]   MatchingUnit         1B  (0 — unsequenced)
 *   [6]   SequenceNumber       4B  (0 — unsequenced)
 *   [10]  TransactionTime      8B
 *   [18]  ClOrdID              20B Text (ClOrdID of the modify request)
 *   [38]  ModifyRejectReason   1B  Text
 *   [39]  Text                 60B Text
 *   [99]  ReservedInternal     1B
 *   [100] NumberOfReturnBitfields 1B
 *   [101] ReturnBitfield¹…ᴺ   NB
 *         Optional fields…
 */
public final class UserModifyRejectedMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x29;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 101;

    // ModifyRejectReason codes (Order Reason Codes p.213)
    public static final byte REASON_TOO_LATE_TO_CANCEL  = (byte) 'J';
    public static final byte REASON_PENDING_FILL        = (byte) 'P';
    public static final byte REASON_NOT_FOUND           = (byte) 'O';
    public static final byte REASON_UNKNOWN             = (byte) 'X';

    private long transactTime;
    private String clOrdID;
    private byte modifyRejectReason;
    private String text;

    private int numberOfBitfields;
    private byte[] bitfields;

    public UserModifyRejectedMessage() {}

    public UserModifyRejectedMessage(String clOrdID, byte reason, String text) {
        this.clOrdID = clOrdID;
        this.modifyRejectReason = reason;
        this.text = text;
        this.transactTime = System.nanoTime();
        this.numberOfBitfields = 0;
        this.bitfields = new byte[0];
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int totalSize = FIXED_SIZE + numberOfBitfields;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1); buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put((byte) 0);               // MatchingUnit = 0 (unsequenced)
        buf.putInt(0);                   // SequenceNumber = 0 (unsequenced)
        buf.putLong(transactTime);
        putText(buf, clOrdID, 20);
        buf.put(modifyRejectReason);
        putText(buf, text, 60);
        buf.put((byte) 0x00);            // ReservedInternal
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    public String getClOrdID() { return clOrdID; }
    public byte getModifyRejectReason() { return modifyRejectReason; }
    public String getText() { return text; }

    @Override
    public String toString() {
        return "UserModifyRejected{clOrdID='" + clOrdID + "', reason=" + (char) modifyRejectReason
                + ", text='" + text + "'}";
    }
}
