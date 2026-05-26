package com.boe.simulator.protocol.types;

public enum Side {
    // Wire values per spec v2.11.90: '1'=Buy, '2'=Sell (ASCII, Alphanumeric field)
    BUY('1'),
    SELL('2');

    private final byte wireValue;

    Side(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isBuy()  { return this == BUY; }
    public boolean isSell() { return this == SELL; }

    /** Accepts spec values ('1'/'2'), legacy numeric (1/2), and old enum values ('B'/'S'). */
    public static Side fromByte(byte b) {
        return switch (b) {
            case (byte) '1', 1 -> BUY;
            case (byte) '2', 2 -> SELL;
            // legacy: pre-Phase-2 wire values stored in RocksDB
            case (byte) 'B' -> BUY;
            case (byte) 'S' -> SELL;
            default -> throw new IllegalArgumentException(
                    "Unknown Side: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
