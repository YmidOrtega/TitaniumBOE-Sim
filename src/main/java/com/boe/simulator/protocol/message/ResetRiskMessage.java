package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Reset Risk — Table 53 (p.98), spec v2.11.90
 * Member→Cboe inbound message. MessageType = 0x56. Fixed size: 50 bytes.
 *
 *   [0]   StartOfMessage      2B
 *   [2]   MessageLength       2B  (= 48)
 *   [4]   MessageType         1B  = 0x56
 *   [5]   MatchingUnit        1B
 *   [6]   SequenceNumber      4B
 *   [10]  RiskStatusID        16B Text
 *   [26]  RiskReset           8B  Text (S, F, C, G, T, E — may be combined)
 *   [34]  TargetMatchingUnit  1B  Binary (0 = all units)
 *   [35]  Reserved            3B
 *   [38]  ClearingFirm        4B  Alpha
 *   [42]  RiskRoot            6B  Alphanumeric
 *   [48]  CustomGroupID       2B  Binary (0 = none)
 */
public final class ResetRiskMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x56;
    private static final int SIZE = 50;

    private byte matchingUnit;
    private int sequenceNumber;
    private String riskStatusID = "";
    private String riskReset = "";
    private int targetMatchingUnit;
    private String clearingFirm = "";
    private String riskRoot = "";
    private int customGroupId;
    private String fieldError;

    public ResetRiskMessage() {}

    public static ResetRiskMessage parse(byte[] data) {
        if (data == null || data.length < SIZE) throw new IllegalArgumentException("Reset Risk is shorter than 50 bytes");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get(4) != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x56");
        ResetRiskMessage msg = new ResetRiskMessage();
        msg.matchingUnit = buf.get(5);
        msg.sequenceNumber = buf.getInt(6);
        msg.riskStatusID = msg.text(data, 10, 16, "RiskStatusID", FieldCharset.TEXT);
        msg.riskReset = msg.text(data, 26, 8, "RiskReset", FieldCharset.TEXT);
        msg.targetMatchingUnit = data[34] & 0xFF;
        msg.clearingFirm = msg.text(data, 38, 4, "ClearingFirm", FieldCharset.ALPHA);
        msg.riskRoot = msg.text(data, 42, 6, "RiskRoot", FieldCharset.ALPHANUMERIC);
        msg.customGroupId = buf.getShort(48) & 0xFFFF;
        return msg;
    }

    private String text(byte[] data, int offset, int length, String field, FieldCharset charset) {
        byte[] raw = java.util.Arrays.copyOfRange(data, offset, offset + length);
        if (fieldError == null) fieldError = charset.check(field, raw);
        int end = length;
        while (end > 0 && raw[end - 1] == 0) end--;
        return new String(raw, 0, end, StandardCharsets.US_ASCII);
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        ByteBuffer buf = ByteBuffer.allocate(SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (SIZE - 2)).put(MESSAGE_TYPE).put(matchingUnit).putInt(sequenceNumber);
        putText(buf, riskStatusID, 16);
        putText(buf, riskReset, 8);
        buf.put((byte) targetMatchingUnit).put(new byte[3]);
        putText(buf, clearingFirm, 4);
        putText(buf, riskRoot, 6);
        buf.putShort((short) customGroupId);
        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        byte[] src = s.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        buf.put(bytes);
    }

    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getRiskStatusID() { return riskStatusID; }
    public String getRiskReset() { return riskReset; }
    public int getTargetMatchingUnit() { return targetMatchingUnit; }
    public String getClearingFirm() { return clearingFirm; }
    public String getRiskRoot() { return riskRoot; }
    public int getCustomGroupId() { return customGroupId; }
    public String getFieldError() { return fieldError; }

    public void setSequenceNumber(int v) { sequenceNumber = v; }
    public void setRiskStatusID(String v) { riskStatusID = v; }
    public void setRiskReset(String v) { riskReset = v; }
    public void setTargetMatchingUnit(int v) { targetMatchingUnit = v; }
    public void setClearingFirm(String v) { clearingFirm = v; }
    public void setRiskRoot(String v) { riskRoot = v; }
    public void setCustomGroupId(int v) { customGroupId = v; }
}
