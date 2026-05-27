package com.boe.simulator.protocol.types;

import java.util.HashMap;
import java.util.Map;

public enum RoutingInst {
    POST_ONLY('P'),
    ROUTABLE('R'),
    BOOK_ONLY('B'),
    SLIDE_TO_LIMIT('S'),
    DONT_SLIDE('X');

    private final byte wireValue;

    private static final Map<Byte, RoutingInst> BY_WIRE = new HashMap<>(values().length * 2);

    static {
        for (RoutingInst ri : values()) {
            BY_WIRE.put(ri.wireValue, ri);
        }
    }

    RoutingInst(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static RoutingInst fromByte(byte b) {
        return BY_WIRE.getOrDefault(b, BOOK_ONLY);
    }
}
