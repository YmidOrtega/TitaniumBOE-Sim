package com.boe.simulator.protocol.types;

import java.util.HashMap;
import java.util.Map;

/**
 * BOE message type codes — 1-byte, little-endian on the wire.
 * Source: Cboe Titanium U.S. Options BOE Specification v2.11.90, Table 134-135 (p.216).
 */
public enum MessageType {

    // ── Member → Cboe (session) ───────────────────────────────────────────────
    LOGIN_REQUEST(0x37),
    LOGOUT_REQUEST(0x02),
    CLIENT_HEARTBEAT(0x03),

    // ── Member → Cboe (orders) ────────────────────────────────────────────────
    NEW_ORDER(0x38),
    CANCEL_ORDER(0x39),
    MODIFY_ORDER(0x3A),
    NEW_ORDER_CROSS(0x41),
    PURGE_ORDERS(0x47),
    NEW_COMPLEX_ORDER(0x4B),
    NEW_COMPLEX_INSTRUMENT(0x4C),
    NEW_ORDER_CROSS_MULTILEG(0x5A),
    QUOTE_UPDATE(0x55),
    RESET_RISK(0x56),
    QUOTE_UPDATE_SHORT(0x59),

    // ── Cboe → Member (session) ───────────────────────────────────────────────
    LOGIN_RESPONSE(0x24),
    LOGOUT(0x08),
    SERVER_HEARTBEAT(0x09),
    REPLAY_COMPLETE(0x13),

    // ── Cboe → Member (order responses) ──────────────────────────────────────
    ORDER_ACKNOWLEDGMENT(0x25),
    CROSS_ORDER_ACKNOWLEDGMENT(0x43),
    ORDER_REJECTED(0x26),
    CROSS_ORDER_REJECTED(0x44),
    ORDER_MODIFIED(0x27),
    ORDER_RESTATED(0x28),
    USER_MODIFY_REJECTED(0x29),
    ORDER_CANCELLED(0x2A),
    CROSS_ORDER_CANCELLED(0x46),
    CANCEL_REJECTED(0x2B),
    ORDER_EXECUTION(0x2C),
    TRADE_CANCEL_OR_CORRECT(0x2D),
    PURGE_REJECTED(0x48),
    MASS_CANCEL_ACKNOWLEDGMENT(0x36),
    COMPLEX_INSTRUMENT_ACCEPTED(0x4D),
    COMPLEX_INSTRUMENT_REJECTED(0x4E),
    QUOTE_UPDATE_ACKNOWLEDGMENT(0x51),
    QUOTE_RESTATED(0x52),
    QUOTE_CANCELLED(0x53),
    QUOTE_EXECUTION(0x54),
    RISK_RESET_ACKNOWLEDGMENT(0x57);

    private final byte wireValue;

    private static final Map<Byte, MessageType> BY_WIRE = new HashMap<>(values().length * 2);

    static {
        for (MessageType mt : values()) {
            BY_WIRE.put(mt.wireValue, mt);
        }
    }

    MessageType(int wireValue) {
        this.wireValue = (byte) wireValue;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static MessageType fromByte(byte value) {
        MessageType mt = BY_WIRE.get(value);
        if (mt == null)
            throw new IllegalArgumentException(
                    "Unknown MessageType: 0x" + Integer.toHexString(value & 0xFF));
        return mt;
    }
}
