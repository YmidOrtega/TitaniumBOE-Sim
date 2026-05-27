package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Replay Complete (0x13) — Cboe to Member.
 * Sent after all replayed messages have been delivered; marks end of replay sequence.
 * Source: spec v2.11.90 Table 25 (p. 59).
 * Wire size: 10 bytes (header only, no payload fields).
 */
public final class ReplayCompleteMessage extends SessionMessage {
    private static final byte MESSAGE_TYPE = 0x13;
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private byte matchingUnit;
    private int sequenceNumber;

    public ReplayCompleteMessage() {
        this.matchingUnit = 0;
        this.sequenceNumber = 0;
    }

    public ReplayCompleteMessage(byte matchingUnit, int sequenceNumber) {
        this.matchingUnit = matchingUnit;
        this.sequenceNumber = sequenceNumber;
    }

    public ReplayCompleteMessage(byte[] data) {
        if (data == null || data.length < 10) throw new IllegalArgumentException("Invalid ReplayComplete message data");
        if (data[0] != START_OF_MESSAGE_1 || data[1] != START_OF_MESSAGE_2) throw new IllegalArgumentException("Invalid start of message marker");

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(4);

        byte messageType = buffer.get();
        if (messageType != MESSAGE_TYPE) throw new IllegalArgumentException("Invalid message type: expected 0x13, got 0x" + String.format("%02X", messageType));

        this.matchingUnit = buffer.get();
        this.sequenceNumber = buffer.getInt();
    }

    @Override
    public byte getMessageType() { return MESSAGE_TYPE; }

    @Override
    public byte[] toBytes() {
        // Header only: SOM(2) + MessageLength(2) + MessageType(1) + MatchingUnit(1) + SeqNum(4) = 10 bytes
        int messageLength = 8; // = MessageLength field(2) + type(1) + unit(1) + seqnum(4)
        ByteBuffer buffer = ByteBuffer.allocate(10);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put(MESSAGE_TYPE);
        buffer.put(matchingUnit);
        buffer.putInt(sequenceNumber);
        return buffer.array();
    }

    public byte getMatchingUnit() { return matchingUnit; }
    public int getSequenceNumber() { return sequenceNumber; }
    public void setMatchingUnit(byte matchingUnit) { this.matchingUnit = matchingUnit; }
    public void setSequenceNumber(int sequenceNumber) { this.sequenceNumber = sequenceNumber; }

    @Override
    public String toString() {
        return "ReplayCompleteMessage{matchingUnit=" + matchingUnit + ", sequenceNumber=" + sequenceNumber + '}';
    }
}
