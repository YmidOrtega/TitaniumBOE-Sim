package com.boe.simulator.protocol.types;

public enum Capacity {
    AGENCY('A'),
    MARKET_MAKER('M'),
    PRINCIPAL('P'),
    FIRM('F'),
    BROKER_DEALER('B'),
    JOINT_BACK_OFFICE('J'),
    PROFESSIONAL_CUSTOMER('U'),
    AWAY_MARKET_MAKER('N');

    private final byte wireValue;

    Capacity(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static Capacity fromByte(byte b) {
        for (Capacity c : values()) {
            if (c.wireValue == b) return c;
        }
        throw new IllegalArgumentException(
                "Unknown Capacity: '" + (char) b + "' (0x" + Integer.toHexString(b & 0xFF) + ")");
    }
}
