package com.boe.simulator.protocol.types;

public enum TimeInForce {
    DAY('0'),
    GTC('1'),
    AT_OPEN('2'),
    IOC('3'),
    FOK('4'),
    GTD('6'),
    AT_CLOSE('7');

    private final byte wireValue;

    TimeInForce(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isImmediate() {
        return this == IOC || this == FOK;
    }

    /** Accepts spec wire values ('0'-'7') and legacy numeric values (0-7). */
    public static TimeInForce fromByte(byte b) {
        return switch (b) {
            case (byte) '0', 0 -> DAY;
            case (byte) '1', 1 -> GTC;
            case (byte) '2', 2 -> AT_OPEN;
            case (byte) '3', 3 -> IOC;
            case (byte) '4', 4 -> FOK;
            case (byte) '6', 6 -> GTD;
            case (byte) '7', 7 -> AT_CLOSE;
            default -> throw new IllegalArgumentException(
                    "Unknown TimeInForce: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
