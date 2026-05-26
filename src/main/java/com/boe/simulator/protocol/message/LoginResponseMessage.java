package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

public final class LoginResponseMessage extends BoeProtocolMessage {
    private static final byte MESSAGE_TYPE = 0x24;

    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static final int LOGIN_RESPONSE_TEXT_SIZE = 60;

    private byte loginResponseStatus;
    private String loginResponseText;
    private byte noUnspecifiedUnitReplay;
    private int lastReceivedSequenceNumber;
    private int numberOfUnits;
    private byte matchingUnit;
    private int sequenceNumber;

    // LoginResponseStatus values per spec v2.11.90 Table 19
    public static final byte STATUS_ACCEPTED          = 'A';
    public static final byte STATUS_NOT_AUTHORIZED    = 'N';
    public static final byte STATUS_SESSION_DISABLED  = 'D';
    public static final byte STATUS_SESSION_IN_USE    = 'B';
    public static final byte STATUS_INVALID_SESSION   = 'S';
    public static final byte STATUS_SEQUENCE_AHEAD    = 'Q';
    public static final byte STATUS_INVALID_UNIT      = 'I';
    public static final byte STATUS_INVALID_BITFIELD  = 'F';
    public static final byte STATUS_INVALID_STRUCTURE = 'M';

    public LoginResponseMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 4) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");

        parseMessage(messageData);
    }

    public LoginResponseMessage(byte loginResponseStatus, String loginResponseText, int lastReceivedSequenceNumber, int numberOfUnits) {
        this.loginResponseStatus = loginResponseStatus;
        this.loginResponseText = loginResponseText != null ? loginResponseText : "";
        this.noUnspecifiedUnitReplay = '0';
        this.lastReceivedSequenceNumber = lastReceivedSequenceNumber;
        this.numberOfUnits = numberOfUnits;
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    private void parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        buffer.position(2);

        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + Status(1) + Text(60) + NoUnspecifiedUnitReplay(1) + LastSeq(4) + NumUnits(1) = 73
        int minPayloadSize = 1 + 1 + 4 + 1 + LOGIN_RESPONSE_TEXT_SIZE + 1 + 4 + 1;
        int minMessageLength = 2 + minPayloadSize;

        if (messageLength < minMessageLength) throw new IllegalArgumentException("Message too short: got " + messageLength + ", expected at least " + minMessageLength);

        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x24, got 0x" + String.format("%02X", messageType));

        this.matchingUnit = buffer.get();
        this.sequenceNumber = buffer.getInt();
        this.loginResponseStatus = buffer.get();

        byte[] textBytes = new byte[LOGIN_RESPONSE_TEXT_SIZE];
        buffer.get(textBytes);
        this.loginResponseText = new String(textBytes, StandardCharsets.US_ASCII).trim();

        this.noUnspecifiedUnitReplay = buffer.get();
        this.lastReceivedSequenceNumber = buffer.getInt();
        this.numberOfUnits = buffer.get() & 0xFF;
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        // MessageType(1) + MatchingUnit(1) + SequenceNumber(4) + Status(1) + Text(60) + NoUnspecifiedUnitReplay(1) + LastSeq(4) + NumUnits(1) = 73
        int payloadLength = 1 + 1 + 4 + 1 + LOGIN_RESPONSE_TEXT_SIZE + 1 + 4 + 1;
        int messageLength = payloadLength + 2;
        int totalLength = 2 + messageLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put(MESSAGE_TYPE);
        buffer.put(matchingUnit);
        buffer.putInt(sequenceNumber);
        buffer.put(loginResponseStatus);
        buffer.put(toFixedLengthBytes(loginResponseText, LOGIN_RESPONSE_TEXT_SIZE));
        buffer.put(noUnspecifiedUnitReplay);
        buffer.putInt(lastReceivedSequenceNumber);
        buffer.put((byte) numberOfUnits);

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
    public byte getLoginResponseStatus() { return loginResponseStatus; }
    public String getLoginResponseText() { return loginResponseText; }
    public byte getNoUnspecifiedUnitReplay() { return noUnspecifiedUnitReplay; }
    public int getLastReceivedSequenceNumber() { return lastReceivedSequenceNumber; }
    public int getNumberOfUnits() { return numberOfUnits; }
    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }

    public boolean isAccepted() { return loginResponseStatus == STATUS_ACCEPTED; }
    public boolean isRejected() { return loginResponseStatus != STATUS_ACCEPTED; }

    // Setters
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }
    public void setNoUnspecifiedUnitReplay(byte value) { this.noUnspecifiedUnitReplay = value; }

    @Override
    public String toString() {
        return "LoginResponseMessage{" +
                "status=" + (char) loginResponseStatus +
                ", text='" + loginResponseText + '\'' +
                ", noUnspecifiedUnitReplay=" + (char) noUnspecifiedUnitReplay +
                ", lastReceivedSeq=" + lastReceivedSequenceNumber +
                ", numberOfUnits=" + numberOfUnits +
                ", matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}