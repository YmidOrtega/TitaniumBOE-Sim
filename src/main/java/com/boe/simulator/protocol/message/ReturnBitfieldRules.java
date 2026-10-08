package com.boe.simulator.protocol.message;

import java.util.Map;

/**
 * Return bitfields a member may request per outbound message — "Return Bitfields Per Message"
 * (p.180+), spec v2.11.90. One byte per bitfield (19 per message); a set bit marks a field shown
 * as O (optional) in the spec table. Fields marked "-" or blank cannot be requested.
 */
public final class ReturnBitfieldRules {

    private static final Map<Byte, byte[]> ALLOWED = Map.ofEntries(
            entry((byte) 0x25, "7D 41 DF 2F FF 0D 01 7E 27 2F 00 80 40 00 82 FF 03 22 04"),   // Order Acknowledgment
            entry((byte) 0x43, "0D 41 C5 0F 00 08 00 00 E6 0F 00 80 00 00 82 09 00 02 04"),   // Cross Order Acknowledgment (C1 and EDGX only)
            entry((byte) 0x26, "7D 41 DF 2F 00 0D 00 7E 27 2F 00 80 00 00 82 FF 03 26 04"),   // Order Rejected
            entry((byte) 0x44, "0C 01 C0 07 00 08 00 00 C2 09 00 00 00 00 02 09 00 06 04"),   // Cross Order Rejected (C1 and EDGX Only)
            entry((byte) 0x27, "7D 01 DF 00 FF 0D 00 7E 27 2F 00 00 00 00 00 FD 03 20 00"),   // Order Modified
            entry((byte) 0x28, "7D 41 DF 2F FF 0D 00 7E 27 2F 00 00 00 00 80 FD 03 20 00"),   // Order Restated
            entry((byte) 0x29, "00 00 00 00 00 00 00 00 00 2F 00 00 00 00 00 00 00 00 00"),   // User Modify Rejected
            entry((byte) 0x2A, "7D 41 DF 2F FF 0D 00 7E 27 2F 00 80 00 00 02 FD 03 26 04"),   // Order Cancelled
            entry((byte) 0x46, "0D 41 C3 0F 00 08 00 00 E6 0F 00 00 00 00 02 09 00 02 04"),   // Cross Order Cancelled (C1 and EDGX Only)
            entry((byte) 0x2B, "7D 41 00 2F 00 00 00 06 27 2F 00 80 00 00 08 00 00 00 00"),   // Cancel Rejected
            entry((byte) 0x2C, "7D C1 DF 2F 00 0C 00 7F E7 2F 00 88 DF 10 82 FD 7B 22 07"),   // Order Execution
            entry((byte) 0x2D, "00 41 00 2F 00 00 01 00 27 2D 00 00 00 00 00 00 00 00 00"),   // Trade Cancel or Correct
            entry((byte) 0x48, "00 00 00 00 00 00 00 00 00 00 00 00 00 00 08 00 00 00 00")    // Purge Rejected
    );

    private ReturnBitfieldRules() {}

    static byte[] allowedFor(byte messageType) {
        byte[] allowed = ALLOWED.get(messageType);
        return allowed != null ? allowed : new byte[0];
    }

    // null = valid; otherwise the text for a Login Response with status F
    public static String validate(ReturnBitfields requested) {
        for (Map.Entry<Byte, byte[]> entry : requested.entries().entrySet()) {
            byte messageType = entry.getKey();
            byte[] allowed = ALLOWED.get(messageType);
            if (allowed == null) {
                return String.format("Return bitfields not supported for message type 0x%02X", messageType);
            }
            byte[] mask = entry.getValue();
            for (int i = 0; i < mask.length; i++) {
                int invalid = (mask[i] & 0xFF) & ~(i < allowed.length ? allowed[i] & 0xFF : 0);
                if (invalid != 0) {
                    return String.format("Invalid return bitfield for 0x%02X: byte %d bit %d",
                            messageType, i + 1, Integer.lowestOneBit(invalid));
                }
            }
        }
        return null;
    }

    private static Map.Entry<Byte, byte[]> entry(byte messageType, String hexMask) {
        String[] parts = hexMask.split(" ");
        byte[] mask = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) mask[i] = (byte) Integer.parseInt(parts[i], 16);
        return Map.entry(messageType, mask);
    }
}
