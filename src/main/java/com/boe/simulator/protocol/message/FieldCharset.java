package com.boe.simulator.protocol.message;

/**
 * Character sets of the spec data types (p.5). Values are NUL (0x00) filled on the right.
 */
enum FieldCharset {
    ALPHA("A-Z, a-z"),
    ALPHANUMERIC("A-Z, a-z, 0-9"),
    TEXT("printable ASCII"),
    CLORDID("33-126 except ,;|@\"");

    private final String description;

    FieldCharset(String description) {
        this.description = description;
    }

    boolean allows(byte b) {
        boolean letter = (b >= 'A' && b <= 'Z') || (b >= 'a' && b <= 'z');
        return switch (this) {
            case ALPHA -> letter;
            case ALPHANUMERIC -> letter || (b >= '0' && b <= '9');
            case TEXT -> b >= 0x20 && b <= 0x7E;
            case CLORDID -> b >= 33 && b <= 126 && b != ',' && b != ';' && b != '|' && b != '@' && b != '"';
        };
    }

    String check(String field, byte[] raw) {
        int i = 0;
        while (i < raw.length && raw[i] != 0) {
            if (!allows(raw[i])) return "Invalid character 0x" + String.format("%02X", raw[i]) + " in " + field + " (" + description + ")";
            i++;
        }
        while (i < raw.length) {
            if (raw[i] != 0) return field + " must be NUL (0x00) filled on the right";
            i++;
        }
        return null;
    }
}
