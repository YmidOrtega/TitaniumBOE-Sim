package com.boe.simulator.protocol.message;

import java.util.Arrays;

public class BoeMessage {
    private final byte[] data;
    private final int messageLength;

    public BoeMessage(byte[] data) {
        if (data == null || data.length < 2) throw new IllegalArgumentException("Message data must be at least 2 bytes");
        this.data = Arrays.copyOf(data, data.length);
        this.messageLength = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public int getMessageLength() {
        return messageLength;
    }

    public byte[] getPayload() {
        final int FIXED_BODY_SIZE = 6;
        final int LENGTH_FIELD_SIZE = 2;
        final int PAYLOAD_OFFSET = LENGTH_FIELD_SIZE + FIXED_BODY_SIZE; // 8

        int payloadLength = messageLength - LENGTH_FIELD_SIZE - FIXED_BODY_SIZE;

        if (payloadLength < 0) return new byte[0];
        byte[] payload = new byte[payloadLength];

        System.arraycopy(this.data, PAYLOAD_OFFSET, payload, 0, payloadLength);

        return payload;
    }

    public short getLengthField() {
        return (short) ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8));
    }

    @Override
    public String toString() {
        return "BoeMessage{length=" + messageLength + ", lengthField=" + getLengthField() + "}";
    }
}
