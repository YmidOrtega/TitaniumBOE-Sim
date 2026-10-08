package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reset Risk Acknowledgment — Table 108 (p.152), spec v2.11.90
 * Cboe→Member, unsequenced. Fixed size: 27 bytes (MessageLength 25).
 *
 *   [0]   StartOfMessage    2B
 *   [2]   MessageLength     2B
 *   [4]   MessageType       1B  = 0x57
 *   [5]   MatchingUnit      1B  (0)
 *   [6]   SequenceNumber    4B  (0)
 *   [10]  RiskStatusID      16B Text
 *   [26]  RiskResetResult   1B  Text
 */
public final class RiskResetAcknowledgmentMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x57;
    private static final int SIZE = 27;

    public static final byte RESULT_IGNORED = (byte) ' ';
    public static final byte RESULT_EMPTY_RESET = (byte) 'E';
    public static final byte RESULT_INVALID_MATCHING_UNIT = (byte) 'M';
    public static final byte RESULT_INVALID_RISK_ROOT = (byte) 'U';
    public static final byte RESULT_SUCCESS = (byte) 'Y';
    public static final byte RESULT_INVALID_CLEARING_FIRM = (byte) 'c';
    public static final byte RESULT_IN_REPLAY = (byte) 'y';

    private final String riskStatusID;
    private final byte riskResetResult;

    public RiskResetAcknowledgmentMessage(String riskStatusID, byte riskResetResult) {
        this.riskStatusID = riskStatusID != null ? riskStatusID : "";
        this.riskResetResult = riskResetResult;
    }

    public static RiskResetAcknowledgmentMessage fromBytes(byte[] data) {
        if (data == null || data.length < SIZE) throw new IllegalArgumentException("Reset Risk Acknowledgment is shorter than 27 bytes");
        int end = 26;
        while (end > 10 && data[end - 1] == 0) end--;
        return new RiskResetAcknowledgmentMessage(new String(data, 10, end - 10, StandardCharsets.US_ASCII), data[26]);
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (SIZE - 2)).put(MESSAGE_TYPE).put((byte) 0).putInt(0);
        byte[] id = new byte[16];
        byte[] src = riskStatusID.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, id, 0, Math.min(src.length, 16));
        buf.put(id).put(riskResetResult);
        return buf.array();
    }

    public String getRiskStatusID() { return riskStatusID; }
    public byte getRiskResetResult() { return riskResetResult; }
}
