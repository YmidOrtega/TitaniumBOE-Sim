package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class LogoutMessage {
    private static final byte MESSAGE_TYPE = 0x02;
    
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    private byte matchingUnit;
    private int sequenceNumber;

    public LogoutMessage() {
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    public LogoutMessage(byte matchingUnit, int sequenceNumber) {
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
    }

    public byte[] toBytes() {
        // Calculate message length:
        // StartOfMessage(2) + MessageLength(2) + MessageType(1) + MatchingUnit(1) + SequenceNumber(4)
        int messageLength = 2 + 2 + 1 + 1 + 4;
        
        ByteBuffer buffer = ByteBuffer.allocate(messageLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        
        // Message Length (2 bytes) - length from MessageType onwards
        buffer.putShort((short) (messageLength - 4));
        
        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);
        
        // Matching Unit (1 byte)
        buffer.put(matchingUnit);
        
        // Sequence Number (4 bytes)
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
        return "LogoutMessage{" +
                "matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}