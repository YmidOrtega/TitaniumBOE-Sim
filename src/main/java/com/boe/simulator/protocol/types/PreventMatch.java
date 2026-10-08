package com.boe.simulator.protocol.types;

import java.nio.charset.StandardCharsets;

/**
 * PreventMatch (List of Optional Fields, p.207): MTP Modifier, Unique ID Level and an optional
 * Trading Group ID (0 when absent).
 */
public record PreventMatch(char modifier, char level, char tradingGroup) {

    public static final char CANCEL_NEWEST = 'N';
    public static final char CANCEL_OLDEST = 'O';
    public static final char CANCEL_BOTH = 'B';
    public static final char CANCEL_SMALLEST = 'S';
    public static final char DECREMENT = 'D';
    public static final char DECREMENT_LEAVES_ONLY = 'd';
    public static final char FIRM_LEVEL = 'F';
    public static final char EFID_LEVEL = 'M';

    public static final PreventMatch PORT_DEFAULT = new PreventMatch(CANCEL_OLDEST, FIRM_LEVEL, (char) 0);

    /** null when the field is all NUL (not specified). */
    public static PreventMatch fromBytes(byte[] raw) {
        if (raw == null || (raw[0] == 0 && raw[1] == 0 && raw[2] == 0)) return null;
        char modifier = (char) raw[0], level = (char) raw[1], group = (char) raw[2];
        if ("NOBSDd".indexOf(modifier) < 0) throw new IllegalArgumentException("Invalid PreventMatch MTP Modifier '" + modifier + "'");
        if (level != FIRM_LEVEL && level != EFID_LEVEL) throw new IllegalArgumentException("Invalid PreventMatch Unique ID Level '" + level + "'");
        if (group != 0 && !Character.isLetterOrDigit(group)) throw new IllegalArgumentException("Invalid PreventMatch Trading Group ID '" + group + "'");
        return new PreventMatch(modifier, level, group);
    }

    public boolean decrements() {
        return modifier == DECREMENT || modifier == DECREMENT_LEAVES_ONLY;
    }

    public byte[] toBytes() {
        return new String(new char[]{modifier, level, tradingGroup}).getBytes(StandardCharsets.US_ASCII);
    }
}
