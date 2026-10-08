package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

import com.boe.simulator.protocol.types.BoeTime;

/**
 * Mass Cancel Acknowledgment — Table 110 (p.153), spec v2.11.90
 * Cboe→Member, unsequenced: MatchingUnit=0, SequenceNumber=0.
 *
 *   [0]   StartOfMessage        2B
 *   [2]   MessageLength         2B  (= 42)
 *   [4]   MessageType           1B  = 0x36
 *   [5]   MatchingUnit          1B  (0 — unsequenced)
 *   [6]   SequenceNumber        4B  (0 — unsequenced)
 *   [10]  TransactionTime       8B  DateTime
 *   [18]  MassCancelID          20B Text
 *   [38]  CancelledOrderCount   4B  Binary
 *   [42]  ReservedInternal      1B
 *   [43]  SourceMatchingUnit    1B  (0 unless MassCancelInst 2nd character = I)
 */
public final class MassCancelAcknowledgmentMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x36;
    private static final int TOTAL_SIZE = 44;

    private final long transactTime;
    private final String massCancelId;
    private final int cancelledOrderCount;

    public MassCancelAcknowledgmentMessage(String massCancelId, int cancelledOrderCount) {
        this(BoeTime.nowEpochNanos(), massCancelId, cancelledOrderCount);
    }

    private MassCancelAcknowledgmentMessage(long transactTime, String massCancelId, int cancelledOrderCount) {
        this.transactTime = transactTime;
        this.massCancelId = massCancelId != null ? massCancelId : "";
        this.cancelledOrderCount = cancelledOrderCount;
    }

    public static MassCancelAcknowledgmentMessage fromBytes(byte[] data) {
        if (data == null || data.length < TOTAL_SIZE) throw new IllegalArgumentException("Mass Cancel Acknowledgment is shorter than 44 bytes");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get(4) != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x36");
        long transactTime = buf.getLong(10);
        byte[] id = new byte[20];
        buf.position(18);
        buf.get(id);
        int end = id.length;
        while (end > 0 && id[end - 1] == 0) end--;
        return new MassCancelAcknowledgmentMessage(transactTime, new String(id, 0, end, StandardCharsets.US_ASCII), buf.getInt(38));
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
        byte[] id = new byte[20];
        byte[] src = massCancelId.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, id, 0, Math.min(src.length, 20));
        buf.put(id);
        buf.putInt(cancelledOrderCount);
        buf.put((byte) 0);
        buf.put((byte) 0);
        return buf.array();
    }

    public String getMassCancelId() { return massCancelId; }
    public int getCancelledOrderCount() { return cancelledOrderCount; }
    public long getTransactTime() { return transactTime; }
}
