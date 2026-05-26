package com.boe.simulator.protocol.types;

/**
 * BOE message type codes — 1-byte, little-endian on the wire.
 * Source: Cboe Titanium U.S. Options BOE Specification v2.11.90, p. 216.
 */
public enum MessageType {

    // ── Member → Cboe (session) ───────────────────────────────────────────────
    LOGIN_REQUEST(0x37),
    LOGOUT_REQUEST(0x02),
    CLIENT_HEARTBEAT(0x03),

    // ── Cboe → Member (session) ───────────────────────────────────────────────
    LOGIN_RESPONSE(0x24),
    LOGOUT(0x08),
    SERVER_HEARTBEAT(0x09),
    REPLAY_COMPLETE(0x13),

    // ── Member → Cboe (orders) ────────────────────────────────────────────────
    NEW_ORDER(0x38),
    NEW_ORDER_SHORT(0x39),
    NEW_ORDER_CROSS(0x3A),
    NEW_COMPLEX_INSTRUMENT(0x3C),
    NEW_COMPLEX_ORDER(0x3D),
    NEW_COMPLEX_ORDER_SHORT(0x3E),
    NEW_ORDER_CROSS_MULTILEG(0x3F),
    CANCEL_ORDER(0x45),
    MASS_CANCEL_ORDER(0x46),
    MODIFY_ORDER(0x4A),
    QUOTE_UPDATE(0x59),
    PURGE_ORDERS(0x62),
    RESET_RISK(0x63),

    // ── Cboe → Member (order responses) ──────────────────────────────────────
    ORDER_ACKNOWLEDGMENT(0x25),
    ORDER_REJECTED(0x26),
    ORDER_MODIFIED(0x27),
    ORDER_RESTATED(0x28),
    USER_MODIFY_REJECTED(0x29),
    ORDER_CANCELLED(0x2A),
    CANCEL_REJECTED(0x2B),
    ORDER_EXECUTION(0x2C),
    TRADE_CANCEL_OR_CORRECT(0x2D),
    MASS_CANCEL_ACKNOWLEDGMENT(0x99),
    PURGE_NOTIFICATION(0x9B);

    private final byte wireValue;

    MessageType(int wireValue) {
        this.wireValue = (byte) wireValue;
    }

    public byte wireValue() {
        return wireValue;
    }

    public static MessageType fromByte(byte value) {
        for (MessageType mt : values()) {
            if (mt.wireValue == value) return mt;
        }
        throw new IllegalArgumentException(
                "Unknown MessageType: 0x" + Integer.toHexString(value & 0xFF));
    }
}
