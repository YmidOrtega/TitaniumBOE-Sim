package com.boe.simulator.protocol.message;

import java.util.Arrays;

public class BoeMessage {
    private final byte[] data;
    private final int length;

    public BoeMessage(byte[] data) {
        if (data == null || data.length < 2) throw new IllegalArgumentException("Message data must be at least 2 bytes");
        this.data = Arrays.copyOf(data, data.length);
        this.length = data.length;
    }

    public byte[] getData() {
        return Arrays.copyOf(data, data.length);
    }

    public int getLength() {
        return length;
    }

    public byte[] getPayload() {
        if (data.length <= 2) return new byte[0];
        return Arrays.copyOfRange(data, 2, data.length);
    }

    public short getLengthField() {
        return (short) ((data[0] & 0xFF) | ((data[1] & 0xFF) << 8));
    }

    @Override
    public String toString() {
        return "BoeMessage{length=" + length + ", lengthField=" + getLengthField() + "}";
    }
}
