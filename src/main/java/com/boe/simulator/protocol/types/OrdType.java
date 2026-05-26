package com.boe.simulator.protocol.types;

public enum OrdType {
    MARKET('1'),
    LIMIT('2');

    private final byte wireValue;

    OrdType(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    /** Accepts spec wire values ('1'/'2') and legacy numeric values (1/2). */
    public static OrdType fromByte(byte b) {
        return switch (b) {
            case (byte) '1', 1 -> MARKET;
            case (byte) '2', 2 -> LIMIT;
            default -> throw new IllegalArgumentException(
                    "Unknown OrdType: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
