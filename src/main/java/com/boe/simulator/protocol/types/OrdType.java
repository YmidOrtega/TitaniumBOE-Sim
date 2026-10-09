package com.boe.simulator.protocol.types;

public enum OrdType {
    MARKET('1'),
    LIMIT('2'),
    STOP('3'),
    STOP_LIMIT('4');

    private final byte wireValue;

    OrdType(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isStop() {
        return this == STOP || this == STOP_LIMIT;
    }

    /** Accepts spec wire values ('1'-'4') and legacy numeric values (1-4). */
    public static OrdType fromByte(byte b) {
        return switch (b) {
            case (byte) '1', 1 -> MARKET;
            case (byte) '2', 2 -> LIMIT;
            case (byte) '3', 3 -> STOP;
            case (byte) '4', 4 -> STOP_LIMIT;
            default -> throw new IllegalArgumentException(
                    "Unknown OrdType: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
