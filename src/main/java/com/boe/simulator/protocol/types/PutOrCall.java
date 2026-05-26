package com.boe.simulator.protocol.types;

public enum PutOrCall {
    // Wire values per spec v2.11.90: '0'=Put, '1'=Call (ASCII, Alphanumeric field)
    PUT('0'),
    CALL('1');

    private final byte wireValue;

    PutOrCall(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isPut()  { return this == PUT; }
    public boolean isCall() { return this == CALL; }

    /** Accepts spec values ('0'/'1'), legacy ('P'/'C'), and raw numeric (0/1). */
    public static PutOrCall fromByte(byte b) {
        return switch (b) {
            case (byte) '0', 0 -> PUT;
            case (byte) '1', 1 -> CALL;
            // legacy: pre-Phase-2 wire values stored in RocksDB
            case (byte) 'P' -> PUT;
            case (byte) 'C' -> CALL;
            default -> throw new IllegalArgumentException(
                    "Unknown PutOrCall: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
