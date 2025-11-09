package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class LoginRequestMessage {
    private static final byte MESSAGE_TYPE = 0x37;

    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static final int SESSION_SUB_ID_SIZE = 4;
    private static final int USERNAME_SIZE = 4;
    private static final int PASSWORD_SIZE = 10;

    private final String sessionSubID;
    private final String username;
    private final String password;
    private byte matchingUnit;
    private int sequenceNumber;

    // Optional parameter groups
    private final byte numberOfParamGroups;

    public LoginRequestMessage(String username, String password) {
        this(username, password, "", (byte) 0);
    }

    public LoginRequestMessage(String username, String password, String sessionSubID) {
        this(username, password, sessionSubID, (byte) 0);
    }

    public LoginRequestMessage(String username, String password, String sessionSubID, byte matchingUnit) {
        if (username == null || username.isEmpty() || username.length() > USERNAME_SIZE) throw new IllegalArgumentException("Username must be between 1 and " + USERNAME_SIZE + " characters");
        if (password == null || password.isEmpty() || password.length() > PASSWORD_SIZE) throw new IllegalArgumentException("Password must be between 1 and " + PASSWORD_SIZE + " characters");
        if (sessionSubID != null && sessionSubID.length() > SESSION_SUB_ID_SIZE) throw new IllegalArgumentException("Session Sub ID must be at most " + SESSION_SUB_ID_SIZE + " characters");

        this.username = username;
        this.password = password;
        this.sessionSubID = sessionSubID != null ? sessionSubID : "";
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = 0;
        this.numberOfParamGroups = 0;
    }

    public byte[] toBytes() {
        // Calculate message length:
        // Total payload (after the MessageLength field) = 1 + 1 + 4 + 4 + 4 + 10 + 1 = 25 bytes
        // MessageLength = Payload + 2 (for the MessageLength field itself)
        int payloadLength = 1 + 1 + 4 + SESSION_SUB_ID_SIZE + USERNAME_SIZE + PASSWORD_SIZE + 1;
        
        // MessageLength = Payload + 2 (the MessageLength field itself)
        int messageLength = payloadLength + 2;

        // Total length = StartOfMessage (2 bytes) + MessageLength
        int totalLength = 2 + messageLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Start of Message (2 bytes)
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

        // Session Sub ID (4 bytes, padded with spaces 0x20)
        buffer.put(toFixedLengthBytes(sessionSubID, SESSION_SUB_ID_SIZE));

        // Username (4 bytes, padded with spaces 0x20)
        buffer.put(toFixedLengthBytes(username, USERNAME_SIZE));

        // Password (10 bytes, padded with spaces 0x20)
        buffer.put(toFixedLengthBytes(password, PASSWORD_SIZE));

        // Number of Parameter Groups (1 byte)
        buffer.put(numberOfParamGroups);

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

    public static LoginRequestMessage parseFromBytes(byte[] data) {
        if (data == null || data.length < 29) throw new IllegalArgumentException("Invalid LoginRequest message data");

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Skip StartOfMessage (2 bytes)
        buffer.position(2);

        // MessageLength (2 bytes)
        @SuppressWarnings("unused")
        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != 0x37) throw new IllegalArgumentException("Invalid message type: expected 0x37, got 0x" + String.format("%02X", messageType));

        // MatchingUnit (1 byte)
        byte matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        int sequenceNumber = buffer.getInt();

        // SessionSubID (4 bytes)
        byte[] sessionSubIDBytes = new byte[4];
        buffer.get(sessionSubIDBytes);
        String sessionSubID = new String(sessionSubIDBytes, StandardCharsets.US_ASCII).trim();

        // Username (4 bytes)
        byte[] usernameBytes = new byte[4];
        buffer.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.US_ASCII).trim();

        // Password (10 bytes)
        byte[] passwordBytes = new byte[10];
        buffer.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.US_ASCII).trim();

        // Create a message
        LoginRequestMessage msg = new LoginRequestMessage(username, password, sessionSubID, matchingUnit);
        msg.setSequenceNumber(sequenceNumber);

        // NumberOfParamGroups (1 byte) - if there are more bytes
        if (buffer.remaining() > 0) {
            @SuppressWarnings("unused")
            int numberOfParamGroups = buffer.get() & 0xFF;
        }

        return msg;
    }

    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }

    public String getSessionSubID() {
        return sessionSubID;
    }

    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "LoginRequestMessage{" +
                "username='" + username + '\'' +
                ", sessionSubID='" + sessionSubID + '\'' +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}