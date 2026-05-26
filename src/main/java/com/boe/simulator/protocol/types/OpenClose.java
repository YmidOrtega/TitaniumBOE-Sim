package com.boe.simulator.protocol.types;

public enum OpenClose {
    OPEN('O'),
    CLOSE('C'),
    NONE(' ');

    private final byte wireValue;

    OpenClose(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static OpenClose fromByte(byte b) {
        return switch (b) {
            case (byte) 'O'            -> OPEN;
            case (byte) 'C'            -> CLOSE;
            case (byte) ' ', (byte) 'N', 0 -> NONE;
            default -> throw new IllegalArgumentException(
                    "Unknown OpenClose: '" + (char) b + "'");
        };
    }
}
