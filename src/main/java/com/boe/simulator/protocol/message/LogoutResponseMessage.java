package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LogoutResponseMessage extends SessionMessage {
    private static final byte MESSAGE_TYPE = 0x08;
    
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    // Field sizes
    private static final int LOGOUT_REASON_SIZE = 60;
    
    private byte logoutReason;
    private String logoutReasonText;
    private int lastReceivedSequenceNumber;
    private Map<Integer, Integer> unitSequences;
    private byte matchingUnit;
    private int sequenceNumber;
    
    public static final byte REASON_USER_REQUESTED      = (byte) 'U';
    public static final byte REASON_END_OF_DAY          = (byte) 'E';
    public static final byte REASON_ADMIN_LOGOUT        = (byte) 'A';
    public static final byte REASON_PROTOCOL_VIOLATION  = (byte) '!';

    public LogoutResponseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 4) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");
        parseMessage(messageData);
    }

    public LogoutResponseMessage(byte logoutReason, String logoutReasonText, int lastReceivedSequenceNumber, Map<Integer, Integer> unitSequences) {
        this.logoutReason = logoutReason;
        this.logoutReasonText = logoutReasonText != null ? logoutReasonText : "";
        this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
        this.unitSequences = Collections.unmodifiableMap(new LinkedHashMap<>(unitSequences));
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    private void parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // Skip StartOfMessage (2 bytes)
        buffer.position(2);
        
        // Read MessageLength (2 bytes)
        int messageLength = buffer.getShort() & 0xFFFF;
        
        // Expected payload: MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + LogoutReason(1) + LogoutReasonText(60) + LastSeq(4) + NumUnits(1) = 72
        int expectedPayloadSize = 1 + 1 + 4 + 1 + LOGOUT_REASON_SIZE + 4 + 1;
        
        if (messageLength < expectedPayloadSize) throw new IllegalArgumentException("Message too short: got " + messageLength + ", expected at least " + expectedPayloadSize);
        
        // Read MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x08, got 0x" + String.format("%02X", messageType));
        
        // Read MatchingUnit (1 byte)
        this.matchingUnit = buffer.get();
        
        // Read SequenceNumber (4 bytes)
        this.sequenceNumber = buffer.getInt();
        
        // Read LogoutReason (1 byte)
        this.logoutReason = buffer.get();
        
        // Read LogoutReasonText (60 bytes fixed, NUL-padded)
        byte[] textBytes = new byte[LOGOUT_REASON_SIZE];
        buffer.get(textBytes);
        this.logoutReasonText = new String(textBytes, StandardCharsets.US_ASCII).trim();
        
        // Read LastReceivedSequenceNumber (4 bytes)
        this.lastReceivedSequenceNumber = buffer.getInt();
        
        // Read NumberOfUnits (1 byte) + unit/sequence pairs (5 bytes each)
        int numberOfUnits = buffer.get() & 0xFF;
        if (buffer.remaining() < numberOfUnits * 5) throw new IllegalArgumentException("Message too short for " + numberOfUnits + " unit/sequence pairs");
        Map<Integer, Integer> units = new LinkedHashMap<>();
        for (int i = 0; i < numberOfUnits; i++) {
            int unit = buffer.get() & 0xFF;
            units.put(unit, buffer.getInt());
        }
        this.unitSequences = Collections.unmodifiableMap(units);
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        // Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + LogoutReason(1) + LogoutText(60) + LastReceivedSeq(4) + NumUnits(1) + 5 per unit
        int payloadLength = 1 + 1 + 4 + 1 + LOGOUT_REASON_SIZE + 4 + 1 + unitSequences.size() * 5;
        int messageLength = payloadLength + 2;
        int totalLength = 2 + messageLength;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Start of Message (2 bytes) 
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        
        // Message Length (2 bytes)
        buffer.putShort((short) messageLength);
        
        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);
        
        // Matching Unit (1 byte)
        buffer.put(matchingUnit);
        
        // Sequence Number (4 bytes)
        buffer.putInt(sequenceNumber);
        
        // Logout Reason (1 byte)
        buffer.put(logoutReason);
        
        // Logout Reason Text (60 bytes fixed, NUL-padded)
        buffer.put(toFixedLengthBytes(logoutReasonText, LOGOUT_REASON_SIZE));
        
        // Last Received Sequence Number (4 bytes)
        buffer.putInt(lastReceivedSequenceNumber);
        
        // Number Of Units (1 byte) + unit/sequence pairs
        buffer.put((byte) unitSequences.size());
        for (Map.Entry<Integer, Integer> entry : unitSequences.entrySet()) {
            buffer.put(entry.getKey().byteValue());
            buffer.putInt(entry.getValue());
        }
        
        return buffer.array();
    }

    // NUL-padded per BOE spec v2.11.90 (Text field type)
    private static byte[] toFixedLengthBytes(String str, int length) {
        byte[] result = new byte[length];
        if (str != null && !str.isEmpty()) {
            byte[] strBytes = str.getBytes(StandardCharsets.US_ASCII);
            System.arraycopy(strBytes, 0, result, 0, Math.min(strBytes.length, length));
        }
        return result;
    }

    // Getters
    public byte getLogoutReason() {
        return logoutReason;
    }

    public String getLogoutReasonText() {
        return logoutReasonText;
    }

    public int getLastReceivedSequenceNumber() {
        return lastReceivedSequenceNumber;
    }

    public int getNumberOfUnits() {
        return unitSequences.size();
    }

    public Map<Integer, Integer> getUnitSequences() {
        return unitSequences;
    }

    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    // Setters
    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "LogoutResponseMessage{" +
                "reason=" + (char) logoutReason +
                ", text='" + logoutReasonText + '\'' +
                ", lastReceivedSeq=" + lastReceivedSequenceNumber +
                ", units=" + unitSequences +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}