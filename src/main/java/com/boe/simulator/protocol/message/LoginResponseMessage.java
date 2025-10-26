package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class LoginResponseMessage {
    private static final byte MESSAGE_TYPE = 0x07;
    
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    private static final int LOGIN_RESPONSE_TEXT_SIZE = 60;
    
    private byte loginResponseStatus;
    private String loginResponseText;
    private int lastReceivedSequenceNumber;
    private int numberOfUnits;
    private byte matchingUnit;
    private int sequenceNumber;

    public static final byte STATUS_ACCEPTED = 'A';
    public static final byte STATUS_REJECTED = 'R';
    public static final byte STATUS_SESSION_IN_USE = 'S';

    public LoginResponseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 4) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");
        
        parseMessage(messageData);
    }

    public LoginResponseMessage(byte loginResponseStatus, String loginResponseText, int lastReceivedSequenceNumber, int numberOfUnits) {
        this.loginResponseStatus = loginResponseStatus;
        this.loginResponseText = loginResponseText != null ? loginResponseText : "";
        this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
        this.numberOfUnits = numberOfUnits;
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    private void parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // Skip StartOfMessage (2 bytes) - NOT included in MessageLength
        buffer.position(2);
        
        // Read MessageLength (2 bytes) - includes itself but not StartOfMessage
        int messageLength = buffer.getShort() & 0xFFFF;
        
        // Expected payload size (from MessageType onwards)
        // MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + Status(1) + Text(60) + LastSeq(4) + NumUnits(1) = 72
        int expectedPayloadSize = 1 + 1 + 4 + 1 + LOGIN_RESPONSE_TEXT_SIZE + 4 + 1;
        int expectedMessageLength = 2 + expectedPayloadSize;
        
        if (messageLength < expectedMessageLength) throw new IllegalArgumentException("Message too short: got " + messageLength + ", expected at least " + expectedMessageLength);
        
        // Read MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x07, got 0x" + String.format("%02X", messageType));
        
        // Read MatchingUnit (1 byte)
        this.matchingUnit = buffer.get();
        
        // Read SequenceNumber (4 bytes)
        this.sequenceNumber = buffer.getInt();
        
        // Read LoginResponseStatus (1 byte)
        this.loginResponseStatus = buffer.get();
        
        // Read LoginResponseText (60 bytes fixed, space-padded)
        byte[] textBytes = new byte[LOGIN_RESPONSE_TEXT_SIZE];
        buffer.get(textBytes);
        this.loginResponseText = new String(textBytes, StandardCharsets.US_ASCII).trim();
        
        // Read LastReceivedSequenceNumber (4 bytes) - always present
        this.lastReceivedSequenceNumber = buffer.getInt();
        
        // Read NumberOfUnits (1 byte) - always present
        this.numberOfUnits = buffer.get() & 0xFF;
    }

    public byte[] toBytes() {
        // Calculate message length according to BOE spec:
        // Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + Status(1) + Text(60) + LastReceivedSeq(4) + NumUnits(1) = 72 bytes
        // MessageLength = 2 (length field) + Payload(72) = 74
        int payloadLength = 1 + 1 + 4 + 1 + LOGIN_RESPONSE_TEXT_SIZE + 4 + 1;
        int messageLength = payloadLength + 2;

        // Total message = StartOfMessage(2) + MessageLength(2) + Payload(72)
        int totalLength = 2 + messageLength;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Start of Message (2 bytes) - NOT included in MessageLength
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        
        // Message Length (2 bytes) - includes itself and payload, not StartOfMessage
        buffer.putShort((short) messageLength);
        
        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);
        
        // Matching Unit (1 byte)
        buffer.put(matchingUnit);
        
        // Sequence Number (4 bytes)
        buffer.putInt(sequenceNumber);
        
        // Login Response Status (1 byte)
        buffer.put(loginResponseStatus);
        
        // Login Response Text (60 bytes fixed, space-padded, US-ASCII)
        buffer.put(toFixedLengthBytes(loginResponseText, LOGIN_RESPONSE_TEXT_SIZE));
        
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
    public byte getLoginResponseStatus() {
        return loginResponseStatus;
    }

    public String getLoginResponseText() {
        return loginResponseText;
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

    public boolean isAccepted() {
        return loginResponseStatus == STATUS_ACCEPTED;
    }

    public boolean isRejected() {
        return loginResponseStatus == STATUS_REJECTED;
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
        return "LoginResponseMessage{" +
                "status=" + (char) loginResponseStatus +
                ", text='" + loginResponseText + '\'' +
                ", lastReceivedSeq=" + lastReceivedSequenceNumber +
                ", numberOfUnits=" + numberOfUnits +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}