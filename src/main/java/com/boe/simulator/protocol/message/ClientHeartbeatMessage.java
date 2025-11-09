package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ClientHeartbeatMessage {
    private static final byte MESSAGE_TYPE = 0x03;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private byte matchingUnit;
    private int sequenceNumber;

    public ClientHeartbeatMessage() {
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    public ClientHeartbeatMessage(byte matchingUnit, int sequenceNumber) {
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] toBytes() {
        // BOE spec: MessageLength = length of everything AFTER StartOfMessage
        // = MessageLength field itself (2) + MessageType (1) + MatchingUnit (1) + SequenceNumber (4)
        int messageLength = 2 + 1 + 1 + 4; // = 8 bytes
        int totalLength = 2 + messageLength; // = 10 bytes

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);

        // Message Length (2 bytes) - includes itself + payload
        buffer.putShort((short) messageLength);

        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);

        // Matching Unit (1 byte)
        buffer.put(matchingUnit);

        // Sequence Number (4 bytes)
        buffer.putInt(sequenceNumber);

        return buffer.array();
    }

    public static ClientHeartbeatMessage parseFromBytes(byte[] data) {
        if (data == null || data.length < 5) throw new IllegalArgumentException("Invalid ClientHeartbeat message data: expected 5 bytes, got " + (data == null ? "null" : data.length));

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // MatchingUnit (1 byte)
        byte matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        int sequenceNumber = buffer.getInt();

        return new ClientHeartbeatMessage(matchingUnit, sequenceNumber);
    }

    // Getters and setters
    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "ClientHeartbeatMessage{" +
                "matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}