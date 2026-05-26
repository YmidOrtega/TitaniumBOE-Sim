package com.boe.simulator.protocol.types;

/**
 * BOEv3 message type codes (2-byte little-endian on the wire).
 * Phase 2 will wire these into the serializer; for now they document the target values.
 */
public enum MessageType {

    // ── Member → Exchange (session) ──────────────────────────────────────────
    LOGIN_REQUEST(0x0001),
    LOGOUT_REQUEST(0x0002),
    CLIENT_HEARTBEAT(0x0003),

    // ── Exchange → Member (session) ──────────────────────────────────────────
    LOGIN_RESPONSE(0x01F5),
    REPLAY_COMPLETE(0x01F6),
    LOGOUT_RESPONSE(0x01F7),
    SERVER_HEARTBEAT(0x01F8),

    // ── Member → Exchange (orders) ───────────────────────────────────────────
    NEW_ORDER_V1(0x07D1),
    NEW_ORDER_SHORT_V1(0x07D2),
    NEW_ORDER_CROSS_V1(0x07D3),
    NEW_COMPLEX_INSTRUMENT_V1(0x07D5),
    NEW_COMPLEX_ORDER_V1(0x07D6),
    NEW_COMPLEX_ORDER_V2(0x07E7),
    NEW_COMPLEX_ORDER_SHORT_V1(0x07D7),
    NEW_ORDER_CROSS_MULTILEG_V1(0x07D8),
    CANCEL_ORDER_V1(0x07DA),
    MASS_CANCEL_ORDER_V1(0x07DB),

    // ── Exchange → Member (order responses) ──────────────────────────────────
    ORDER_ACKNOWLEDGMENT(0x0025),
    ORDER_REJECTED(0x0026),
    ORDER_MODIFIED(0x0027),
    ORDER_RESTATED(0x0028),
    USER_MODIFY_REJECTED(0x0029),
    ORDER_CANCELLED(0x002A),
    CANCEL_REJECTED(0x002B),
    ORDER_EXECUTION(0x002C),
    TRADE_CANCEL_OR_CORRECT(0x002D);

    private final short wireValue;

    MessageType(int wireValue) {
        this.wireValue = (short) wireValue;
    }

    public short wireValue() {
        return wireValue;
    }

    public static MessageType fromShort(short value) {
        for (MessageType mt : values()) {
            if (mt.wireValue == value) return mt;
        }
        throw new IllegalArgumentException(
                "Unknown MessageType: 0x" + Integer.toHexString(value & 0xFFFF));
    }
}
