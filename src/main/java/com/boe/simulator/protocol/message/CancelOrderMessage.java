package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Cancel Order — Table 36 (p.74), spec v2.11.90
 * Member→Cboe inbound message. MessageType = 0x39.
 *
 * Fixed layout (31 bytes minimum):
 *   [0]   StartOfMessage              2B Binary
 *   [2]   MessageLength               2B Binary
 *   [4]   MessageType                 1B = 0x39
 *   [5]   MatchingUnit                1B (always 0 for inbound)
 *   [6]   SequenceNumber              4B Binary LE
 *   [10]  OrigClOrdID                 20B Text (NUL-padded; all-zero = mass cancel)
 *   [30]  NumberOfCancelOrderBitfields 1B
 *   [31]  CancelOrderBitfield¹        1B (if NumberOfBitfields > 0)
 *   ...   Optional fields
 *
 * Input bitfields (p.175) — every other bit is blank or reserved and cannot be specified:
 *   Byte 1: 0x01=ClearingFirm(4B,Alpha), 0x08=RiskRoot(6B,Text),
 *           0x10=MassCancelId(20B,Text), 0x20=RoutingFirmID(4B,Alpha)
 *   Byte 2: 0x01=MassCancelInst(16B,Text), 0x08=SendTime(8B,DateTime) — SendTime is required
 */
public final class CancelOrderMessage extends ApplicationMessage {
    private static final byte MESSAGE_TYPE = 0x39;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 31; // before bitfields/optional

    private static final int MAX_BITFIELDS = 2;
    private static final int[][] FIELD_LENGTHS = {
        {4, 0, 0, 6, 20, 4, 0, 0},   // 0 = blank or reserved
        {16, 0, 0, 8, 0, 0, 0, 0},
    };

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private String origClOrdID;     // 20B Text, NUL-padded; empty = mass cancel

    // Bitfields
    private int numberOfBitfields;
    private byte[] bitfields;

    // Optional — Byte 1
    private String clearingFirm;         // 4B Alpha
    private String riskRoot;             // 6B Text
    private String massCancelId;         // 20B Text
    private String routingFirmID;        // 4B Alpha

    // Optional — Byte 2
    private String massCancelInst;       // 16B Text
    private long sendTime;               // 8B DateTime

    private String fieldError;
    private String charsetError;

    public CancelOrderMessage() {
        this.numberOfBitfields = 0;
        this.bitfields = new byte[0];
    }

    public CancelOrderMessage(String origClOrdID) {
        this();
        this.origClOrdID = origClOrdID;
    }

    public static CancelOrderMessage parse(byte[] data) {
        if (data == null || data.length < FIXED_SIZE)
            throw new IllegalArgumentException("Invalid CancelOrder data: too short");

        CancelOrderMessage msg = new CancelOrderMessage();
        ByteBuffer buf = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buf.position(4); // skip SOM(2) + MsgLen(2)

        byte messageType = buf.get();
        if (messageType != MESSAGE_TYPE)
            throw new IllegalArgumentException(
                "Invalid message type: expected 0x39, got 0x" + String.format("%02X", messageType));

        msg.matchingUnit = buf.get();
        msg.sequenceNumber = buf.getInt();

        byte[] origBytes = new byte[20];
        buf.get(origBytes);
        msg.origClOrdID = stripNul(origBytes);
        msg.charsetError = FieldCharset.TEXT.check("OrigClOrdID", origBytes);

        msg.numberOfBitfields = buf.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);

        msg.parseOptionalFields(buf);
        if (msg.fieldError == null) msg.fieldError = msg.charsetError;
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buf) {
        for (int i = 0; i < bitfields.length; i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & (1 << bit)) == 0) continue;

                int length = i < MAX_BITFIELDS ? FIELD_LENGTHS[i][bit] : 0;
                if (length == 0) {
                    fieldError = "Bitfield " + (i + 1) + " bit " + (1 << bit) + " cannot be specified on Cancel Order";
                    return;
                }
                if (buf.remaining() < length) {
                    fieldError = "Cancel Order is too short for its optional fields";
                    return;
                }
                byte[] value = new byte[length];
                buf.get(value);
                assign(i, 1 << bit, value);
            }
        }
        if (!hasBit(1, 0x08)) fieldError = "SendTime is required on Cancel Order";
    }

    private void assign(int bitfield, int bit, byte[] value) {
        if (bitfield == 0 && charsetError == null) {
            charsetError = switch (bit) {
                case 0x01 -> FieldCharset.ALPHA.check("ClearingFirm", value);
                case 0x08 -> FieldCharset.TEXT.check("RiskRoot", value);
                case 0x10 -> FieldCharset.TEXT.check("MassCancelID", value);
                case 0x20 -> FieldCharset.ALPHA.check("RoutingFirmID", value);
                default -> null;
            };
        }
        if (bitfield == 0) {
            switch (bit) {
                case 0x01 -> clearingFirm = stripNul(value);
                case 0x08 -> riskRoot = stripNul(value);
                case 0x10 -> massCancelId = stripNul(value);
                case 0x20 -> routingFirmID = stripNul(value);
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        } else {
            switch (bit) {
                case 0x01 -> massCancelInst = stripNul(value);
                case 0x08 -> sendTime = ByteBuffer.wrap(value).order(ByteOrder.LITTLE_ENDIAN).getLong();
                default -> throw new IllegalStateException("No field for bit " + bit);
            }
        }
    }

    private boolean hasBit(int bitfield, int bit) {
        return bitfield < bitfields.length && (bitfields[bitfield] & bit) != 0;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        int optSize = optionalSize();
        int totalSize = FIXED_SIZE + numberOfBitfields + optSize;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put(SOM1); buf.put(SOM2);
        buf.putShort((short) (totalSize - 2));
        buf.put(MESSAGE_TYPE);
        buf.put(matchingUnit);
        buf.putInt(sequenceNumber);
        putText(buf, origClOrdID, 20);
        buf.put((byte) numberOfBitfields);
        if (numberOfBitfields > 0) buf.put(bitfields, 0, numberOfBitfields);

        if (hasBit(0, 0x01)) putText(buf, clearingFirm, 4);
        if (hasBit(0, 0x08)) putText(buf, riskRoot, 6);
        if (hasBit(0, 0x10)) putText(buf, massCancelId, 20);
        if (hasBit(0, 0x20)) putText(buf, routingFirmID, 4);
        if (hasBit(1, 0x01)) putText(buf, massCancelInst, 16);
        if (hasBit(1, 0x08)) buf.putLong(sendTime);
        return buf.array();
    }

    private int optionalSize() {
        int size = 0;
        for (int i = 0; i < Math.min(bitfields.length, MAX_BITFIELDS); i++) {
            for (int bit = 0; bit < 8; bit++) {
                if ((bitfields[i] & (1 << bit)) != 0) size += FIELD_LENGTHS[i][bit];
            }
        }
        return size;
    }

    private void setBit(int bitfield, int bit) {
        if (bitfields.length <= bitfield) {
            byte[] grown = new byte[bitfield + 1];
            System.arraycopy(bitfields, 0, grown, 0, bitfields.length);
            bitfields = grown;
            numberOfBitfields = grown.length;
        }
        bitfields[bitfield] |= (byte) bit;
    }

    // All string fields (Alpha, Alphanumeric, Text) use NUL (0x00) padding per spec p.10
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

    public boolean isMassCancel() {
        return origClOrdID == null || origClOrdID.isBlank();
    }

    // MassCancelInst characters (List of Optional Fields, p.204); null when the character is absent
    public Character massCancelInstChar(int position) {
        if (massCancelInst == null || massCancelInst.length() < position) return null;
        return massCancelInst.charAt(position - 1);
    }

    public boolean hasClearingFirm() { return hasBit(0, 0x01); }
    public boolean hasMassCancelInst() { return hasBit(1, 0x01); }

    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getOrigClOrdID() { return origClOrdID; }
    public String getClearingFirm() { return clearingFirm; }
    public String getRiskRoot() { return riskRoot; }
    public String getMassCancelId() { return massCancelId; }
    public String getMassCancelInst() { return massCancelInst; }
    public String getRoutingFirmID() { return routingFirmID; }
    public long getSendTime() { return sendTime; }
    public String getFieldError() { return fieldError; }

    // Setters
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setOrigClOrdID(String origClOrdID) { this.origClOrdID = origClOrdID; }
    public void setClearingFirm(String clearingFirm) { this.clearingFirm = clearingFirm; setBit(0, 0x01); }
    public void setRiskRoot(String riskRoot) { this.riskRoot = riskRoot; setBit(0, 0x08); }
    public void setMassCancelId(String massCancelId) { this.massCancelId = massCancelId; setBit(0, 0x10); }
    public void setRoutingFirmID(String routingFirmID) { this.routingFirmID = routingFirmID; setBit(0, 0x20); }
    public void setMassCancelInst(String massCancelInst) { this.massCancelInst = massCancelInst; setBit(1, 0x01); }
    public void setSendTime(long sendTime) { this.sendTime = sendTime; setBit(1, 0x08); }

    @Override
    public String toString() {
        if (isMassCancel())
            return "CancelOrder{MASS: firm='" + clearingFirm + "', root='" + riskRoot + "', inst='" + massCancelInst + "'}";
        return "CancelOrder{origClOrdID='" + origClOrdID + "'}";
    }
}
