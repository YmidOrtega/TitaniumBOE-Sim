package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class LogoutResponseMessage {
    private static final byte MESSAGE_TYPE = 0x08;
    
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    // Field sizes
    private static final int LOGOUT_REASON_SIZE = 60;
    
    private byte logoutReason;
    private String logoutReasonText;
    private int lastReceivedSequenceNumber;
    private int numberOfUnits;
    private byte matchingUnit;
    private int sequenceNumber;
    
    public static final byte REASON_USER_REQUESTED = (byte) 'U';
    public static final byte REASON_ADMIN_LOGOUT = (byte) 'A';
    public static final byte REASON_END_OF_DAY = (byte) 'E';

    public LogoutResponseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 4) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");
        parseMessage(messageData);
    }

    public LogoutResponseMessage(byte logoutReason, String logoutReasonText, int lastReceivedSequenceNumber, int numberOfUnits) {
        this.logoutReason = logoutReason;
        this.logoutReasonText = logoutReasonText != null ? logoutReasonText : "";
        this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
        this.numberOfUnits = numberOfUnits;
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
        
        // Read LogoutReasonText (60 bytes fixed, space-padded)
        byte[] textBytes = new byte[LOGOUT_REASON_SIZE];
        buffer.get(textBytes);
        this.logoutReasonText = new String(textBytes, StandardCharsets.US_ASCII).trim();
        
        // Read LastReceivedSequenceNumber (4 bytes)
        this.lastReceivedSequenceNumber = buffer.getInt();
        
        // Read NumberOfUnits (1 byte)
        this.numberOfUnits = buffer.get() & 0xFF;
    }

    public byte[] toBytes() {
        // Calculate message length according to BOE spec:
        // Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + LogoutReason(1) + LogoutText(60) + LastReceivedSeq(4) + NumUnits(1) = 72 bytesint messageLength = 1 + 1 + 4 + 1 + LOGOUT_REASON_SIZE + 4 + 1;
        int payloadLength = 1 + 1 + 4 + 1 + LOGOUT_REASON_SIZE + 4 + 1;

        // MessageLength = 2 (length field) + Payload(72) = 74
        int messageLength = payloadLength + 2;
        
        // Total message = StartOfMessage(2) + MessageLength(2) + Payload(72) = 76 bytes
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
        
        // Logout Reason Text (60 bytes fixed, space-padded)
        buffer.put(toFixedLengthBytes(logoutReasonText, LOGOUT_REASON_SIZE));
        
        // Last Received Sequence Number (4 bytes)
        buffer.putInt(lastReceivedSequenceNumber);
        
        // Number Of Units (1 byte)
        buffer.put((byte) numberOfUnits);
        
        return buffer.array();
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
        return numberOfUnits;
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
                ", numberOfUnits=" + numberOfUnits +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}