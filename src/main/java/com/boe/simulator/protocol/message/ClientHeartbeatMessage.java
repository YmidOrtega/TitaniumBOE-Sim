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
        // Calculate message length according to BOE spec:
        // MessageLength = from MessageType to end
        // Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) = 6 bytes
        int payloadLength = 1 + 1 + 4;
        
        // Total message = StartOfMessage(2) + MessageLength(2) + Payload(6) = 10 bytes
        int totalLength = 2 + 2 + payloadLength;
        
        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        
        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        
        // Message Length (2 bytes)
        buffer.putShort((short) payloadLength);
        
        // Message Type (1 byte)
        buffer.put(MESSAGE_TYPE);
        
        // Matching Unit (1 byte)
        buffer.put(matchingUnit);
        
        // Sequence Number (4 bytes)
        buffer.putInt(sequenceNumber);
        
        return buffer.array();
    }

    public static ClientHeartbeatMessage parseFromBytes(byte[] data) {
        if (data == null || data.length < 10) throw new IllegalArgumentException("Invalid ClientHeartbeat message data");

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);

        // Skip StartOfMessage (2 bytes)
        buffer.position(2);

        // MessageLength (2 bytes)
        int messageLength = buffer.getShort() & 0xFFFF;

        // MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != 0x03) throw new IllegalArgumentException("Invalid message type: expected 0x03, got 0x" + String.format("%02X", messageType));

        // MatchingUnit (1 byte)
        byte matchingUnit = buffer.get();

        // SequenceNumber (4 bytes)
        int sequenceNumber = buffer.getInt();

        // Create message
        ClientHeartbeatMessage msg = new ClientHeartbeatMessage(matchingUnit, sequenceNumber);

        return msg;
    }


    public void setMatchingUnit(byte matchingUnit) {
        this.matchingUnit = matchingUnit;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public byte getMatchingUnit() {
        return matchingUnit;
    }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    @Override
    public String toString() {
        return "ClientHeartbeatMessage{" +
                "matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}