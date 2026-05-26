package com.boe.simulator.protocol.types;

public enum RoutingInst {
    POST_ONLY('P'),
    ROUTABLE('R'),
    BOOK_ONLY('B'),
    SLIDE_TO_LIMIT('S'),
    DONT_SLIDE('X');

    private final byte wireValue;

    RoutingInst(char c) {
        this.wireValue = (byte) c;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static RoutingInst fromByte(byte b) {
        for (RoutingInst ri : values()) {
            if (ri.wireValue == b) return ri;
        }
        return BOOK_ONLY;
    }
}
