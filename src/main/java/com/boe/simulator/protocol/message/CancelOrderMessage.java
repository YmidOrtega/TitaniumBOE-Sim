package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class CancelOrderMessage {
    private static final byte MESSAGE_TYPE = 0x39;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    // Header
    private byte matchingUnit;
    private int sequenceNumber;

    // Required fields
    private String origClOrdID;       // 20 bytes - ClOrdID de la orden a cancelar

    // Optional fields (controlled by bitfields)
    private int numberOfBitfields;
    private byte[] bitfields;

    private String clearingFirm;      // 4 bytes
    private String riskRoot;          // 6 bytes - For mass cancel
    private String massCancelId;      // 20 bytes - For mass cancel acknowledgment
    private String massCancelInst;    // 16 bytes - Mass cancel instructions
    private long sendTime;            // 8 bytes - Timestamp (nanoseconds)

    public CancelOrderMessage() {
    }

    public CancelOrderMessage(String origClOrdID) {
        this.origClOrdID = origClOrdID;
        this.numberOfBitfields = 0;
    }

    public static CancelOrderMessage parse(byte[] data) {
        if (data == null || data.length < 31) {
            throw new IllegalArgumentException("Invalid CancelOrder message data");
        }

        CancelOrderMessage msg = new CancelOrderMessage();
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Skip StartOfMessage (2 bytes)
        buffer.position(2);

        // MessageLength (2 bytes)
        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) {
            throw new IllegalArgumentException("Invalid message type: expected 0x39, got 0x" + String.format("%02X", messageType));
        }

        // MatchingUnit (1 byte)
        msg.matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        msg.sequenceNumber = buffer.getInt();

        // OrigClOrdID (20 bytes)
        byte[] origClOrdIDBytes = new byte[20];
        buffer.get(origClOrdIDBytes);
        msg.origClOrdID = new String(origClOrdIDBytes, StandardCharsets.US_ASCII).trim();

        // NumberOfBitfields (1 byte)
        msg.numberOfBitfields = buffer.get() & 0xFF;

        // Bitfields
        if (msg.numberOfBitfields > 0) {
            msg.bitfields = new byte[msg.numberOfBitfields];
            buffer.get(msg.bitfields);

            // Parse optional fields
            msg.parseOptionalFields(buffer);
        }

        return msg;
    }

    public byte[] toBytes() {
        // Calculate size
        int baseSize = 2 + 2 + 1 + 1 + 4 + 20 + 1; // Header + OrigClOrdID + NumberOfBitfields
        int optionalSize = calculateOptionalSize();
        int totalSize = baseSize + numberOfBitfields + optionalSize;

        ByteBuffer buffer = ByteBuffer.allocate(totalSize);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // StartOfMessage
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // MessageLength
        buffer.putShort((short)(totalSize - 2));

        // MessageType
        buffer.put(MESSAGE_TYPE);

        // MatchingUnit
        buffer.put(matchingUnit);

        // SequenceNumber
        buffer.putInt(sequenceNumber);

        // OrigClOrdID (20 bytes)
        buffer.put(toFixedLengthBytes(origClOrdID, 20));

        // NumberOfBitfields
        buffer.put((byte)numberOfBitfields);

        // Bitfields (if any)
        if (numberOfBitfields > 0 && bitfields != null) {
            buffer.put(bitfields);

            // Optional fields in order
            writeOptionalFields(buffer);
        }

        return buffer.array();
    }

    private void parseOptionalFields(ByteBuffer buffer) {
        if (numberOfBitfields == 0) return;

        // Bitfield 1
        if (bitfields.length >= 1) {
            byte bf1 = bitfields[0];

            // Bit 0: ClearingFirm
            if ((bf1 & 0x01) != 0) {
                byte[] cfBytes = new byte[4];
                buffer.get(cfBytes);
                clearingFirm = new String(cfBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 3: RiskRoot
            if ((bf1 & 0x08) != 0) {
                byte[] rrBytes = new byte[6];
                buffer.get(rrBytes);
                riskRoot = new String(rrBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 4: MassCancelId
            if ((bf1 & 0x10) != 0) {
                byte[] mcIdBytes = new byte[20];
                buffer.get(mcIdBytes);
                massCancelId = new String(mcIdBytes, StandardCharsets.US_ASCII).trim();
            }
        }

        // Bitfield 2
        if (bitfields.length >= 2) {
            byte bf2 = bitfields[1];

            // Bit 0: MassCancelInst
            if ((bf2 & 0x01) != 0) {
                byte[] mcInstBytes = new byte[16];
                buffer.get(mcInstBytes);
                massCancelInst = new String(mcInstBytes, StandardCharsets.US_ASCII).trim();
            }

            // Bit 3: SendTime
            if ((bf2 & 0x08) != 0) {
                sendTime = buffer.getLong();
            }
        }
    }

    private void writeOptionalFields(ByteBuffer buffer) {
        if (bitfields.length >= 1) {
            // Bit 0: ClearingFirm
            if ((bitfields[0] & 0x01) != 0 && clearingFirm != null) {
                buffer.put(toFixedLengthBytes(clearingFirm, 4));
            }

            // Bit 3: RiskRoot
            if ((bitfields[0] & 0x08) != 0 && riskRoot != null) {
                buffer.put(toFixedLengthBytes(riskRoot, 6));
            }

            // Bit 4: MassCancelId
            if ((bitfields[0] & 0x10) != 0 && massCancelId != null) {
                buffer.put(toFixedLengthBytes(massCancelId, 20));
            }
        }

        if (bitfields.length >= 2) {
            // Bit 0 of bitfield 2: MassCancelInst
            if ((bitfields[1] & 0x01) != 0 && massCancelInst != null) {
                buffer.put(toFixedLengthBytes(massCancelInst, 16));
            }

            // Bit 3 of bitfield 2: SendTime
            if ((bitfields[1] & 0x08) != 0) {
                buffer.putLong(sendTime);
            }
        }
    }

    private int calculateOptionalSize() {
        int size = 0;
        if (bitfields == null || bitfields.length == 0) return size;

        if ((bitfields[0] & 0x01) != 0) size += 4;  // ClearingFirm
        if ((bitfields[0] & 0x08) != 0) size += 6;  // RiskRoot
        if ((bitfields[0] & 0x10) != 0) size += 20; // MassCancelId

        if (bitfields.length >= 2) {
            if ((bitfields[1] & 0x01) != 0) size += 16; // MassCancelInst
            if ((bitfields[1] & 0x08) != 0) size += 8;  // SendTime
        }

        return size;
    }

    private byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        Arrays.fill(result, (byte) 0x20);

        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            int copyLength = Math.min(strBytes.length, length);
            System.arraycopy(strBytes, 0, result, 0, copyLength);
        }

        return result;
    }

    public boolean isMassCancel() {
        return origClOrdID == null || origClOrdID.isEmpty() ||
                origClOrdID.replaceAll("\u0000", "").trim().isEmpty();
    }

    public void setOrigClOrdID(String origClOrdID) {
        this.origClOrdID = origClOrdID;
    }

    public MassCancelType getMassCancelType() {
        if (massCancelInst == null || massCancelInst.isEmpty()) {
            return MassCancelType.NONE;
        }

        char firstChar = massCancelInst.charAt(0);
        return switch (firstChar) {
            case 'F' -> MassCancelType.FIRM;           // Cancel by clearing firm
            case 'S' -> MassCancelType.SYMBOL;         // Cancel by symbol
            case 'M' -> MassCancelType.MARKETMAKER;    // Cancel market maker orders
            case 'C' -> MassCancelType.CUSTOMER;       // Cancel customer orders
            case 'A' -> MassCancelType.ALL;            // Cancel all orders
            default -> MassCancelType.NONE;
        };
    }

    public boolean isLockoutRequested() {
        if (massCancelInst == null || massCancelInst.length() < 3) return false;
        return massCancelInst.charAt(2) == 'L';
    }

    // Getters
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public String getOrigClOrdID() { return origClOrdID; }
    public String getClearingFirm() { return clearingFirm; }
    public String getRiskRoot() { return riskRoot; }
    public String getMassCancelId() { return massCancelId; }
    public String getMassCancelInst() { return massCancelInst; }
    public long getSendTime() { return sendTime; }

    // Setters
    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public void setClearingFirm(String clearingFirm) {
        this.clearingFirm = clearingFirm;
    }

    public void setRiskRoot(String riskRoot) {
        this.riskRoot = riskRoot;
    }

    public void setMassCancelId(String massCancelId) {
        this.massCancelId = massCancelId;
    }

    public void setMassCancelInst(String massCancelInst) {
        this.massCancelInst = massCancelInst;
    }

    public void setSendTime(long sendTime) {
        this.sendTime = sendTime;
    }

    @Override
    public String toString() {
        if (isMassCancel()) {
            return "CancelOrder{MASS CANCEL: firm='" + clearingFirm + "', root='" + riskRoot +
                    "', type=" + getMassCancelType() + ", lockout=" + isLockoutRequested() + "}";
        } else {
            return "CancelOrder{origClOrdID='" + origClOrdID + "'}";
        }
    }

    public enum MassCancelType {
        NONE,
        FIRM,       // Cancel by clearing firm
        SYMBOL,     // Cancel by symbol
        MARKETMAKER,
        CUSTOMER,
        ALL
    }
}
