package com.boe.simulator.protocol.types;

import java.util.HashMap;
import java.util.Map;

public enum Capacity {
    AGENCY('A'),
    CUSTOMER('C'),
    MARKET_MAKER('M'),
    PRINCIPAL('P'),
    FIRM('F'),
    BROKER_DEALER('B'),
    JOINT_BACK_OFFICE('J'),
    PROFESSIONAL_CUSTOMER('U'),
    AWAY_MARKET_MAKER('N');

    private final byte wireValue;

    private static final Map<Byte, Capacity> BY_WIRE = new HashMap<>(values().length * 2);

    static {
        for (Capacity c : values()) {
            BY_WIRE.put(c.wireValue, c);
        }
    }

    Capacity(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static Capacity fromByte(byte b) {
        Capacity c = BY_WIRE.get(b);
        if (c == null)
            throw new IllegalArgumentException(
                    "Unknown Capacity: '" + (char) b + "' (0x" + Integer.toHexString(b & 0xFF) + ")");
        return c;
    }
}
