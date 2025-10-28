package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class ServerHeartbeatMessage {
    private static final byte MESSAGE_TYPE = 0x04;
    
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    
    private byte matchingUnit;
    private int sequenceNumber;

    public ServerHeartbeatMessage(byte[] messageData) {
        if (messageData == null || messageData.length < 10) throw new IllegalArgumentException("Invalid message data");
        if (messageData[0] != START_OF_MESSAGE_1 || messageData[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");
        
        parseMessage(messageData);
    }

    public ServerHeartbeatMessage() {
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    public ServerHeartbeatMessage(byte matchingUnit, int sequenceNumber) {
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
    }

    private void parseMessage(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // Skip StartOfMessage (2 bytes)
        buffer.position(2);
        
        // Read MessageLength (2 bytes)
        int messageLength = buffer.getShort() & 0xFFFF;

        if (messageLength != 8) throw new IllegalArgumentException("Invalid message length for ServerHeartbeat: " + messageLength);
        
        // Read MessageType (1 byte)
        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x04, got 0x" + String.format("%02X", messageType));
        
        // Read MatchingUnit (1 byte)
        this.matchingUnit = buffer.get();
        
        // Read SequenceNumber (4 bytes)
        this.sequenceNumber = buffer.getInt();
    }

    public byte[] toBytes() {
        // Calculate message length according to BOE spec:
        // MessageLength = from MessageType to end
        // Payload = MessageType(1) + MatchingUnit(1) + SequenceNumber(4) = 6 bytes
        int payloadLength = 1 + 1 + 4;

        // MessageLength = Payload(6) + 2 (length field itself) = 8
        int messageLength = payloadLength + 2;

        // Total message = StartOfMessage(2) + MessageLength(8) = 10 bytes
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
        
        return buffer.array();
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
        return "ServerHeartbeatMessage{" +
                "matchingUnit=" + matchingUnit +
                ", sequenceNumber=" + sequenceNumber +
                '}';
    }
}