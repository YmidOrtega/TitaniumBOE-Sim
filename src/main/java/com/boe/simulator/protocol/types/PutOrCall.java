package com.boe.simulator.protocol.types;

public enum PutOrCall {
    PUT('P'),
    CALL('C');

    private final byte wireValue;

    PutOrCall(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public boolean isPut()  { return this == PUT; }
    public boolean isCall() { return this == CALL; }

    /** Accepts spec wire values ('P'/'C') and legacy values ('0'/'1' / 0/1). */
    public static PutOrCall fromByte(byte b) {
        return switch (b) {
            case (byte) 'P', (byte) '0', 0 -> PUT;
            case (byte) 'C', (byte) '1', 1 -> CALL;
            default -> throw new IllegalArgumentException(
                    "Unknown PutOrCall: 0x" + Integer.toHexString(b & 0xFF));
        };
    }
}
