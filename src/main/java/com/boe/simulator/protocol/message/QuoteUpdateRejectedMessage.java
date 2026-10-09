package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import com.boe.simulator.protocol.types.BoeTime;

/**
 * Quote Update Rejected — Table 77 (p.122), spec v2.11.90
 * Cboe→Member, unsequenced: MatchingUnit=0, SequenceNumber=0.
 *
 *   [0]   StartOfMessage      2B
 *   [2]   MessageLength       2B  (= 50)
 *   [4]   MessageType         1B  = 0x58
 *   [5]   MatchingUnit        1B  (0 — unsequenced)
 *   [6]   SequenceNumber      4B  (0 — unsequenced)
 *   [10]  TransactionTime     8B  DateTime
 *   [18]  QuoteUpdateID       16B Text
 *   [34]  QuoteRejectReason   1B  Text (Quote Reason Codes, p.214)
 *   [35]  Reserved            17B
 */
public final class QuoteUpdateRejectedMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x58;
    private static final int TOTAL_SIZE = 52;

    public static final byte REASON_NOT_ENABLED_FOR_QUOTES = (byte) 'F';

    private final long transactTime;
    private final String quoteUpdateID;
    private final byte quoteRejectReason;

    public QuoteUpdateRejectedMessage(String quoteUpdateID, byte quoteRejectReason) {
        this(BoeTime.nowEpochNanos(), quoteUpdateID, quoteRejectReason);
    }

    private QuoteUpdateRejectedMessage(long transactTime, String quoteUpdateID, byte quoteRejectReason) {
        this.transactTime = transactTime;
        this.quoteUpdateID = quoteUpdateID != null ? quoteUpdateID : "";
        this.quoteRejectReason = quoteRejectReason;
    }

    public static QuoteUpdateRejectedMessage fromBytes(byte[] data) {
        if (data == null || data.length < TOTAL_SIZE) throw new IllegalArgumentException("Quote Update Rejected is shorter than 52 bytes");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get(4) != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x58");
        byte[] id = new byte[16];
        buf.position(18);
        buf.get(id);
        int end = id.length;
        while (end > 0 && id[end - 1] == 0) end--;
        return new QuoteUpdateRejectedMessage(buf.getLong(10), new String(id, 0, end, StandardCharsets.US_ASCII), buf.get(34));
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(TOTAL_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA);
        buf.putShort((short) (TOTAL_SIZE - 2));
        buf.put(MESSAGE_TYPE);
        buf.put((byte) 0);
        buf.putInt(0);
        buf.putLong(transactTime);
        byte[] id = new byte[16];
        byte[] src = quoteUpdateID.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, id, 0, Math.min(src.length, 16));
        buf.put(id);
        buf.put(quoteRejectReason);
        return buf.array();
    }

    public String getQuoteUpdateID() { return quoteUpdateID; }
    public byte getQuoteRejectReason() { return quoteRejectReason; }
    public long getTransactTime() { return transactTime; }
}
