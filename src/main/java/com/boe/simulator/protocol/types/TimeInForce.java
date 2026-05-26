package com.boe.simulator.protocol.types;

public enum TimeInForce {
    DAY('0'),
    GTC('1'),
    AT_OPEN('2'),
    IOC('3');

    private final byte wireValue;

    TimeInForce(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    /** Accepts spec wire values ('0'-'3') and legacy numeric values (0-3). */
    public static TimeInForce fromByte(byte b) {
        return switch (b) {
            case (byte) '0', 0 -> DAY;
            case (byte) '1', 1 -> GTC;
            case (byte) '2', 2 -> AT_OPEN;
            case (byte) '3', 3 -> IOC;
            default -> throw new IllegalArgumentException(
                    "Unknown TimeInForce: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
