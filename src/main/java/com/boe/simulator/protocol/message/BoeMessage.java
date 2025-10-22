package com.boe.simulator.protocol.message;

import java.util.Arrays;

public class BoeMessage {
    private static final int HEADER_SIZE = 4;
    
    private final byte[] data;
    private final int length;

    public BoeMessage(byte[] data) {
        if (data == null || data.length < HEADER_SIZE) throw new IllegalArgumentException("Message data must be at least " + HEADER_SIZE + " bytes");
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
        if (data.length <= HEADER_SIZE) return new byte[0];
        return Arrays.copyOfRange(data, HEADER_SIZE, data.length);
    }

    public short getLengthField() {
        if (data.length < HEADER_SIZE) return 0;
        return (short) ((data[2] & 0xFF) | ((data[3] & 0xFF) << 8));
    }

    public byte getMessageType() {
        if (data.length <= HEADER_SIZE) return 0;
        return data[HEADER_SIZE];
    }

    public boolean hasValidStartMarker() {
        if (data.length < 2) return false;
        return data[0] == (byte) 0xBA && data[1] == (byte) 0xBA;
    }

    @Override
    public String toString() {
        return "BoeMessage{" +
                "length=" + length +
                ", lengthField=" + getLengthField() +
                ", messageType=0x" + String.format("%02X", getMessageType()) +
                ", validMarker=" + hasValidStartMarker() +
                '}';
    }
}