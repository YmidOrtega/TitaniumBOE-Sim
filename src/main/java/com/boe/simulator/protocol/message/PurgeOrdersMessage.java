package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Purge Orders — Table 50 (p.95), spec v2.11.90
 * Member→Cboe inbound message. MessageType = 0x47.
 *
 *   [0]   StartOfMessage               2B
 *   [2]   MessageLength                2B
 *   [4]   MessageType                  1B = 0x47
 *   [5]   MatchingUnit                 1B (always 0 inbound)
 *   [6]   SequenceNumber               4B
 *   [10]  Reserved                     1B
 *   [11]  NumberOfPurgeOrdersBitfields 1B
 *         PurgeOrderBitfield¹…ᴺ        NB
 *         CustomGroupIDCnt             1B (0-10)
 *         CustomGroupID¹…ᴺ             2B Binary each
 *         Optional fields…
 *
 * Input bitfields (p.177) — every other bit is blank or reserved and cannot be specified:
 *   Byte 1: 0x01=ClearingFirm(4B,Alpha), 0x04=MassCancelInst(16B,Text), 0x08=RiskRoot(6B,Text),
 *           0x10=MassCancelID(20B,Text), 0x20=RoutingFirmID(4B,Alpha)
 *   Byte 2: 0x40=SendTime(8B,DateTime, required), 0x80=MatchingUnit(1B,Binary)
 */
public final class PurgeOrdersMessage extends ApplicationMessage {
    public static final byte MESSAGE_TYPE = 0x47;
    private static final int MIN_SIZE = 13;
    private static final int MAX_CUSTOM_GROUP_IDS = 10;

    private static final int[][] FIELD_LENGTHS = {
        {4, 0, 16, 6, 20, 4, 0, 0},   // 0 = blank or reserved
        {0, 0, 0, 0, 0, 0, 8, 1},
    };

    private byte matchingUnit;
    private int sequenceNumber;
    private byte[] bitfields = new byte[0];
    private final List<Integer> customGroupIds = new ArrayList<>();

    private String clearingFirm;
    private String massCancelInst;
    private String riskRoot;
    private String massCancelId;
    private String routingFirmID;
    private long sendTime;
    private int targetMatchingUnit;

    private String fieldError;
    private String charsetError;

    public PurgeOrdersMessage() {}

    public static PurgeOrdersMessage parse(byte[] data) {
        if (data == null || data.length < MIN_SIZE) throw new IllegalArgumentException("Purge Orders is shorter than 13 bytes");
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        if (buf.get(4) != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x47");

        PurgeOrdersMessage msg = new PurgeOrdersMessage();
        msg.matchingUnit = buf.get(5);
        msg.sequenceNumber = buf.getInt(6);
        buf.position(11);
        int numberOfBitfields = buf.get() & 0xFF;
        if (buf.remaining() < numberOfBitfields + 1) {
            msg.fieldError = "Purge Orders is too short for its bitfields";
            return msg;
        }
        msg.bitfields = new byte[numberOfBitfields];
        buf.get(msg.bitfields);

        int count = buf.get() & 0xFF;
        if (count > MAX_CUSTOM_GROUP_IDS) {
            msg.fieldError = "CustomGroupIDCnt must be between 0 and 10";
            return msg;
        }
        if (buf.remaining() < count * 2) {
            msg.fieldError = "Purge Orders is too short for its CustomGroupIDs";
            return msg;
        }
        for (int i = 0; i < count; i++) msg.customGroupIds.add(buf.getShort() & 0xFFFF);

        msg.parseOptionalFields(buf);
        if (msg.fieldError == null) msg.fieldError = msg.charsetError;
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buf) {
        for (int i = 0; i < bitfields.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & (1 << bit)) == 0) continue;
                int length = i < FIELD_LENGTHS.length ? FIELD_LENGTHS[i][bit] : 0;
                if (length == 0) {
                    fieldError = "Bitfield " + (i + 1) + " bit " + (1 << bit) + " cannot be specified on Purge Orders";
                    return;
                }
                if (buf.remaining() < length) {
                    fieldError = "Purge Orders is too short for its optional fields";
                    return;
                }
                byte[] value = new byte[length];
                buf.get(value);
                assign(i, 1 << bit, value);
            }
        }
        if (!hasBit(1, 0x40)) fieldError = "SendTime is required on Purge Orders";
    }

    private void assign(int bitfield, int bit, byte[] value) {
        if (bitfield == 0) {
            if (charsetError == null) {
                charsetError = switch (bit) {
                    case 0x01 -> FieldCharset.ALPHA.check("ClearingFirm", value);
                    case 0x04 -> FieldCharset.TEXT.check("MassCancelInst", value);
                    case 0x08 -> FieldCharset.TEXT.check("RiskRoot", value);
                    case 0x10 -> FieldCharset.TEXT.check("MassCancelID", value);
                    case 0x20 -> FieldCharset.ALPHA.check("RoutingFirmID", value);
                    default -> null;
                };
            }
            switch (bit) {
                case 0x01 -> clearingFirm = stripNul(value);
                case 0x04 -> massCancelInst = stripNul(value);
                case 0x08 -> riskRoot = stripNul(value);
                case 0x10 -> massCancelId = stripNul(value);
                case 0x20 -> routingFirmID = stripNul(value);
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        } else {
            switch (bit) {
                case 0x40 -> sendTime = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong();
                case 0x80 -> targetMatchingUnit = value[0] & 0xFF;
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        }
    }

    private boolean hasBit(int bitfield, int bit) {
        return bitfield < bitfields.length && (bitfields[bitfield] & bit) != 0;
    }

    private static String stripNul(byte[] b) {
        int end = b.length;
        while (end > 0 && b[end - 1] == 0) end--;
        return new String(b, 0, end, StandardCharsets.US_ASCII);
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int optional = 0;
        for (int i = 0; i < bitfields.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & (1 << bit)) != 0) optional += FIELD_LENGTHS[i][bit];
            }
        }
        int total = 12 + bitfields.length + 1 + customGroupIds.size() * 2 + optional;
        ByteBuffer buf = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) (total - 2)).put(MESSAGE_TYPE).put(matchingUnit).putInt(sequenceNumber);
        buf.put((byte) 0).put((byte) bitfields.length).put(bitfields);
        buf.put((byte) customGroupIds.size());
        for (int id : customGroupIds) buf.putShort((short) id);
        if (hasBit(0, 0x01)) putText(buf, clearingFirm, 4);
        if (hasBit(0, 0x04)) putText(buf, massCancelInst, 16);
        if (hasBit(0, 0x08)) putText(buf, riskRoot, 6);
        if (hasBit(0, 0x10)) putText(buf, massCancelId, 20);
        if (hasBit(0, 0x20)) putText(buf, routingFirmID, 4);
        if (hasBit(1, 0x40)) buf.putLong(sendTime);
        if (hasBit(1, 0x80)) buf.put((byte) targetMatchingUnit);
        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] bytes = new byte[len];
        if (s != null) {
            byte[] src = s.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, bytes, 0, Math.min(src.length, len));
        }
        buf.put(bytes);
    }

    private void setBit(int bitfield, int bit) {
        if (bitfields.length <= bitfield) bitfields = java.util.Arrays.copyOf(bitfields, bitfield + 1);
        bitfields[bitfield] |= (byte) bit;
    }

    // MassCancelInst characters (p.204); null when the character is absent
    public Character massCancelInstChar(int position) {
        if (massCancelInst == null || massCancelInst.length() < position) return null;
        return massCancelInst.charAt(position - 1);
    }

    public boolean hasClearingFirm() { return hasBit(0, 0x01); }
    public boolean hasMatchingUnitField() { return hasBit(1, 0x80); }

    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public List<Integer> getCustomGroupIds() { return List.copyOf(customGroupIds); }
    public String getClearingFirm() { return clearingFirm; }
    public String getMassCancelInst() { return massCancelInst; }
    public String getRiskRoot() { return riskRoot; }
    public String getMassCancelId() { return massCancelId; }
    public String getRoutingFirmID() { return routingFirmID; }
    public long getSendTime() { return sendTime; }
    public int getTargetMatchingUnit() { return targetMatchingUnit; }
    public String getFieldError() { return fieldError; }

    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setCustomGroupIds(List<Integer> ids) { customGroupIds.clear(); customGroupIds.addAll(ids); }
    public void setClearingFirm(String v) { clearingFirm = v; setBit(0, 0x01); }
    public void setMassCancelInst(String v) { massCancelInst = v; setBit(0, 0x04); }
    public void setRiskRoot(String v) { riskRoot = v; setBit(0, 0x08); }
    public void setMassCancelId(String v) { massCancelId = v; setBit(0, 0x10); }
    public void setSendTime(long v) { sendTime = v; setBit(1, 0x40); }
    public void setTargetMatchingUnit(int v) { targetMatchingUnit = v; setBit(1, 0x80); }

    @Override
    public String toString() {
        return "PurgeOrders{inst='" + massCancelInst + "', clearingFirm='" + clearingFirm + "', riskRoot='" + riskRoot
                + "', customGroupIds=" + customGroupIds + ", massCancelId='" + massCancelId + "'}";
    }
}
