package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class HeartbeatMessage {
    private static final byte MESSAGE_TYPE = 0x03;
    
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    private byte matchingUnit;
    private int sequenceNumber;

    public HeartbeatMessage() {
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    public HeartbeatMessage(byte matchingUnit, int sequenceNumber) {
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] toBytes() {
        int payloadLength = 1 + 1 + 4;
        int totalLength = 2 + 2 + payloadLength;

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        // Start of Message
        buffer.put((byte) 0xBA);
        buffer.put((byte) 0xBA);

        // Message Length (payload only)
        buffer.putShort((short) payloadLength);

        // Message Type
        buffer.put(MESSAGE_TYPE);

        // Matching Unit
        buffer.put(matchingUnit);

        // Sequence Number
        buffer.putInt(sequenceNumber);

        return buffer.array();
    }

    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    @Override
    public String toString() {
        return "HeartbeatMessage{" +
                "matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}