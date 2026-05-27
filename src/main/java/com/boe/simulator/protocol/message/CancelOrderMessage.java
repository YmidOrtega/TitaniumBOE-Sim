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
 * Bitfield map (p.175):
 *   Byte 1: 0x01=ClearingFirm(4B,Alpha), 0x02=MassCancelLockout(1B),
 *           0x04=MassCancel(1B), 0x08=RiskRoot(6B,Text),
 *           0x10=MassCancelId(20B,Text), 0x20=RoutingFirmID(4B,Alpha),
 *           0x40=ManualOrderIndicator(1B), 0x80=OperatorId(4B,Alpha)
 *   Byte 2: 0x01=MassCancelInst(16B,Text), 0x02=Symbol(8B,Alpha),
 *           0x04=SymbolSfx(reserved), 0x08=SendTime(8B,Binary)
 */
public final class CancelOrderMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x39;
    private static final byte SOM1 = (byte) 0xBA;
    private static final byte SOM2 = (byte) 0xBA;
    private static final int FIXED_SIZE = 31; // before bitfields/optional

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
    private byte massCancelLockout;      // 1B Alphanumeric
    private byte massCancel;             // 1B Alphanumeric
    private String riskRoot;             // 6B Text
    private String massCancelId;         // 20B Text
    private String routingFirmID;        // 4B Alpha
    private byte manualOrderIndicator;   // 1B Alphanumeric
    private String operatorId;           // 4B Alpha

    // Optional — Byte 2
    private String massCancelInst;       // 16B Text
    private String symbol;               // 8B Alphanumeric
    private long sendTime;               // 8B Binary

    public CancelOrderMessage() {}

    public CancelOrderMessage(String origClOrdID) {
        this.origClOrdID = origClOrdID;
        this.numberOfBitfields = 0;
        this.bitfields = new byte[0];
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

        msg.numberOfBitfields = buf.get() & 0xFF;
        msg.bitfields = new byte[msg.numberOfBitfields];
        if (msg.numberOfBitfields > 0) buf.get(msg.bitfields);

        msg.parseOptionalFields(buf);
        return msg;
    }

    private void parseOptionalFields(ByteBuffer buf) {
        if (numberOfBitfields < 1) return;
        byte bf1 = bitfields[0];

        if ((bf1 & 0x01) != 0) { byte[] b = new byte[4]; buf.get(b); clearingFirm = stripNul(b); }
        if ((bf1 & 0x02) != 0) massCancelLockout = buf.get();
        if ((bf1 & 0x04) != 0) massCancel = buf.get();
        if ((bf1 & 0x08) != 0) { byte[] b = new byte[6]; buf.get(b); riskRoot = stripNul(b); }
        if ((bf1 & 0x10) != 0) { byte[] b = new byte[20]; buf.get(b); massCancelId = stripNul(b); }
        if ((bf1 & 0x20) != 0) { byte[] b = new byte[4]; buf.get(b); routingFirmID = stripNul(b); }
        if ((bf1 & 0x40) != 0) manualOrderIndicator = buf.get();
        if ((bf1 & 0x80) != 0) { byte[] b = new byte[4]; buf.get(b); operatorId = stripNul(b); }

        if (numberOfBitfields < 2) return;
        byte bf2 = bitfields[1];

        if ((bf2 & 0x01) != 0) { byte[] b = new byte[16]; buf.get(b); massCancelInst = stripNul(b); }
        if ((bf2 & 0x02) != 0) { byte[] b = new byte[8]; buf.get(b); symbol = stripNul(b); }
        // 0x04 = SymbolSfx (reserved — skip if present; size unknown, assume 0 in practice)
        if ((bf2 & 0x08) != 0) sendTime = buf.getLong();
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

        writeOptionalFields(buf);
        return buf.array();
    }

    private void writeOptionalFields(ByteBuffer buf) {
        if (numberOfBitfields < 1) return;
        byte bf1 = bitfields[0];

        if ((bf1 & 0x01) != 0) putText(buf, clearingFirm, 4);
        if ((bf1 & 0x02) != 0) buf.put(massCancelLockout);
        if ((bf1 & 0x04) != 0) buf.put(massCancel);
        if ((bf1 & 0x08) != 0) putText(buf, riskRoot, 6);
        if ((bf1 & 0x10) != 0) putText(buf, massCancelId, 20);
        if ((bf1 & 0x20) != 0) putText(buf, routingFirmID, 4);
        if ((bf1 & 0x40) != 0) buf.put(manualOrderIndicator);
        if ((bf1 & 0x80) != 0) putText(buf, operatorId, 4);

        if (numberOfBitfields < 2) return;
        byte bf2 = bitfields[1];

        if ((bf2 & 0x01) != 0) putText(buf, massCancelInst, 16);
        if ((bf2 & 0x02) != 0) putText(buf, symbol, 8);
        if ((bf2 & 0x08) != 0) buf.putLong(sendTime);
    }

    private int optionalSize() {
        if (numberOfBitfields < 1) return 0;
        int size = 0;
        byte bf1 = bitfields[0];
        if ((bf1 & 0x01) != 0) size += 4;
        if ((bf1 & 0x02) != 0) size += 1;
        if ((bf1 & 0x04) != 0) size += 1;
        if ((bf1 & 0x08) != 0) size += 6;
        if ((bf1 & 0x10) != 0) size += 20;
        if ((bf1 & 0x20) != 0) size += 4;
        if ((bf1 & 0x40) != 0) size += 1;
        if ((bf1 & 0x80) != 0) size += 4;

        if (numberOfBitfields < 2) return size;
        byte bf2 = bitfields[1];
        if ((bf2 & 0x01) != 0) size += 16;
        if ((bf2 & 0x02) != 0) size += 8;
        if ((bf2 & 0x08) != 0) size += 8;
        return size;
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

    public MassCancelType getMassCancelType() {
        if (massCancelInst == null || massCancelInst.isEmpty()) return MassCancelType.NONE;
        return switch (massCancelInst.charAt(0)) {
            case 'F' -> MassCancelType.FIRM;
            case 'S' -> MassCancelType.SYMBOL;
            case 'M' -> MassCancelType.MARKETMAKER;
            case 'C' -> MassCancelType.CUSTOMER;
            case 'A' -> MassCancelType.ALL;
            default  -> MassCancelType.NONE;
        };
    }

    // Lockout instruction = 3rd character of MassCancelInst (per spec p.204)
    public boolean isLockoutRequested() {
        return massCancelInst != null && massCancelInst.length() >= 3
                && massCancelInst.charAt(2) == 'L';
    }

    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getOrigClOrdID() { return origClOrdID; }
    public String getClearingFirm() { return clearingFirm; }
    public String getRiskRoot() { return riskRoot; }
    public String getMassCancelId() { return massCancelId; }
    public String getMassCancelInst() { return massCancelInst; }
    public String getRoutingFirmID() { return routingFirmID; }
    public String getOperatorId() { return operatorId; }
    public String getSymbol() { return symbol; }
    public long getSendTime() { return sendTime; }

    // Setters
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setOrigClOrdID(String origClOrdID) { this.origClOrdID = origClOrdID; }
    public void setClearingFirm(String clearingFirm) { this.clearingFirm = clearingFirm; }
    public void setRiskRoot(String riskRoot) { this.riskRoot = riskRoot; }
    public void setMassCancelId(String massCancelId) { this.massCancelId = massCancelId; }
    public void setMassCancelInst(String massCancelInst) { this.massCancelInst = massCancelInst; }
    public void setRoutingFirmID(String routingFirmID) { this.routingFirmID = routingFirmID; }
    public void setSymbol(String symbol) { this.symbol = symbol; }
    public void setSendTime(long sendTime) { this.sendTime = sendTime; }

    @Override
    public String toString() {
        if (isMassCancel())
            return "CancelOrder{MASS: firm='" + clearingFirm + "', root='" + riskRoot
                    + "', type=" + getMassCancelType() + ", lockout=" + isLockoutRequested() + "}";
        return "CancelOrder{origClOrdID='" + origClOrdID + "'}";
    }

    public enum MassCancelType {
        NONE, FIRM, SYMBOL, MARKETMAKER, CUSTOMER, ALL
    }
}
