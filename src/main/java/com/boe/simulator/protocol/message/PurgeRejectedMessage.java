package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BoeTime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Purge Rejected — Table 106 (p.150), spec v2.11.90
 * Cboe→Member, unsequenced.
 *
 *   [0]   StartOfMessage           2B
 *   [2]   MessageLength            2B
 *   [4]   MessageType              1B  = 0x48
 *   [5]   MatchingUnit             1B  (0)
 *   [6]   SequenceNumber           4B  (0)
 *   [10]  TransactionTime          8B  DateTime
 *   [18]  PurgeRejectReason        1B  Text (Order Reason Codes, p.213)
 *   [19]  Text                     60B Text
 *   [79]  ReservedInternal         1B
 *   [80]  NumberOfReturnBitfields  1B
 *         ReturnBitfield¹…ᴺ, optional fields (p.192)
 */
public final class PurgeRejectedMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x48;
    private static final int FIXED_SIZE = 80; // before NumberOfReturnBitfields

    private final long transactTime;
    private final byte purgeRejectReason;
    private final String text;
    private ReturnFields returnFields = new ReturnFields();

    public PurgeRejectedMessage(byte purgeRejectReason, String text) {
        this(BoeTime.nowEpochNanos(), purgeRejectReason, text);
    }

    private PurgeRejectedMessage(long transactTime, byte purgeRejectReason, String text) {
        this.transactTime = transactTime;
        this.purgeRejectReason = purgeRejectReason;
        this.text = text != null ? text : "";
    }

    public PurgeRejectedMessage withReturnFields(ReturnFields returnFields) {
        this.returnFields = returnFields;
        return this;
    }

    public static PurgeRejectedMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        int end = 79;
        while (end > 19 && data[end - 1] == 0) end--;
        PurgeRejectedMessage msg = new PurgeRejectedMessage(buf.getLong(10), data[18], new String(data, 19, end - 19, StandardCharsets.US_ASCII));
        buf.position(FIXED_SIZE);
        msg.returnFields = ReturnFields.readFrom(buf);
        return msg;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int total = FIXED_SIZE + returnFields.encodedSize();
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (total - 2)).put(MESSAGE_TYPE).put((byte) 0).putInt(0);
        buf.putLong(transactTime).put(purgeRejectReason);
        byte[] t = new byte[60];
        byte[] src = text.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, t, 0, Math.min(src.length, 60));
        buf.put(t).put((byte) 0);
        returnFields.writeTo(buf);
        return buf.array();
    }

    public byte getPurgeRejectReason() { return purgeRejectReason; }
    public String getText() { return text; }
    public ReturnFields getReturnFields() { return returnFields; }
}
