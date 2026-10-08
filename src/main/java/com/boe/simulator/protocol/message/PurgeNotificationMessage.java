package com.boe.simulator.protocol.message;

import com.boe.simulator.protocol.types.BoeTime;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Purge Notification — Table 112 (p.155), spec v2.11.90
 * Cboe→Member, unsequenced. Sent to the originating ports when a purge uses Acknowledgement Style A.
 *
 *   [0]   StartOfMessage           2B
 *   [2]   MessageLength            2B
 *   [4]   MessageType              1B  = 0x63
 *   [5]   MatchingUnit             1B  (0)
 *   [6]   SequenceNumber           4B  (0)
 *   [10]  TransactionTime          8B  DateTime
 *   [18]  MassCancelID             20B Text
 *   [38]  CancelledOrderCount      4B  Binary
 *   [42]  SourceMatchingUnit       1B  Binary
 *   [43]  ClearingFirm             4B  Alpha
 *   [47]  RiskRoot                 6B  Text
 *   [53]  MassCancelLockOut        1B  Alpha (Y / N)
 *   [54]  ReservedInternal         1B
 *   [55]  NumberOfReturnBitfields  1B  (no field can be requested, p.193)
 */
public final class PurgeNotificationMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x63;
    private static final int SIZE = 56;

    private final long transactTime;
    private final String massCancelId;
    private final int cancelledOrderCount;
    private final int sourceMatchingUnit;
    private final String clearingFirm;
    private final String riskRoot;
    private final boolean lockout;

    public PurgeNotificationMessage(String massCancelId, int cancelledOrderCount, int sourceMatchingUnit,
                                    String clearingFirm, String riskRoot, boolean lockout) {
        this(BoeTime.nowEpochNanos(), massCancelId, cancelledOrderCount, sourceMatchingUnit, clearingFirm, riskRoot, lockout);
    }

    private PurgeNotificationMessage(long transactTime, String massCancelId, int cancelledOrderCount, int sourceMatchingUnit,
                                     String clearingFirm, String riskRoot, boolean lockout) {
        this.transactTime = transactTime;
        this.massCancelId = massCancelId != null ? massCancelId : "";
        this.cancelledOrderCount = cancelledOrderCount;
        this.sourceMatchingUnit = sourceMatchingUnit;
        this.clearingFirm = clearingFirm != null ? clearingFirm : "";
        this.riskRoot = riskRoot != null ? riskRoot : "";
        this.lockout = lockout;
    }

    public static PurgeNotificationMessage fromBytes(byte[] data) {
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        return new PurgeNotificationMessage(buf.getLong(10), text(data, 18, 20), buf.getInt(38), data[42] & 0xFF,
                text(data, 43, 4), text(data, 47, 6), data[53] == 'Y');
    }

    private static String text(byte[] data, int offset, int length) {
        int end = offset + length;
        while (end > offset && data[end - 1] == 0) end--;
        return new String(data, offset, end - offset, StandardCharsets.US_ASCII);
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (SIZE - 2)).put(MESSAGE_TYPE).put((byte) 0).putInt(0);
        buf.putLong(transactTime);
        putText(buf, massCancelId, 20);
        buf.putInt(cancelledOrderCount).put((byte) sourceMatchingUnit);
        putText(buf, clearingFirm, 4);
        putText(buf, riskRoot, 6);
        buf.put((byte) (lockout ? 'Y' : 'N')).put((byte) 0).put((byte) 0);
        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        buf.put(bytes);
    }

    public String getMassCancelId() { return massCancelId; }
    public int getCancelledOrderCount() { return cancelledOrderCount; }
    public int getSourceMatchingUnit() { return sourceMatchingUnit; }
    public String getClearingFirm() { return clearingFirm; }
    public String getRiskRoot() { return riskRoot; }
    public boolean isLockout() { return lockout; }
}
