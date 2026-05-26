package com.boe.simulator.protocol.types;

public enum Side {
    BUY('B'),
    SELL('S');

    private final byte wireValue;

    Side(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isBuy()  { return this == BUY; }
    public boolean isSell() { return this == SELL; }

    /** Accepts spec wire values ('B'/'S') and legacy numeric values (1/2). */
    public static Side fromByte(byte b) {
        return switch (b) {
            case (byte) 'B', 1 -> BUY;
            case (byte) 'S', 2 -> SELL;
            default -> throw new IllegalArgumentException(
                    "Unknown Side: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
