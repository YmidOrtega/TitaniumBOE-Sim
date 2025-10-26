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
        // Total payload (after MessageLength field) = 1 + 1 + 4 + 4 + 4 + 10 + 1 = 25 bytes
        int payloadLength = 1 + 1 + 4 + SESSION_SUB_ID_SIZE + USERNAME_SIZE + PASSWORD_SIZE + 1;
        System.out.println(payloadLength);

        // Total buffer = StartOfMessage(2) + MessageLength(2) + Payload
        int totalLength = 2 + 2 + payloadLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // Message Length (2 bytes) - includes itself
        buffer.putShort((short) payloadLength);

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