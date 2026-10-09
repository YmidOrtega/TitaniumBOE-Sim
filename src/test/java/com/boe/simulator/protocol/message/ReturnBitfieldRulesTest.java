package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ReturnBitfieldRulesTest {

    private static ReturnBitfields groups(String hex) {
        String[] p = hex.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        int count = 0;
        ByteBuffer scan = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);
        while (scan.hasRemaining()) {
            int len = scan.getShort(scan.position()) & 0xFFFF;
            scan.position(scan.position() + len);
            count++;
        }
        return ReturnBitfields.parse(count, ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN));
    }

    @Test
    void noReturnBitfields_isValid() {
        assertNull(ReturnBitfieldRules.validate(ReturnBitfields.empty()));
    }

    @Test
    void specTable14OrderAcknowledgmentGroup_isValid() {
        // 0x25: Symbol, Capacity (byte 2) and Account, ClearingAccount (byte 3) are all "O"
        assertNull(ReturnBitfieldRules.validate(groups("08 00 81 25 03 00 41 05")));
    }

    @Test
    void fieldMarkedDash_isRejectedNamingByteAndBit() {
        // Order Execution, byte 5 bit 64 = BaseLiquidityIndicator, marked "-" (it is a fixed field).
        // The spec's own Table 14 example requests it; the example is "for illustrative purposes only".
        assertEquals("Invalid return bitfield for 0x2C: byte 5 bit 64",
                ReturnBitfieldRules.validate(groups("0B 00 81 2C 06 00 41 07 00 40 00")));
    }

    @Test
    void fieldNotUsedByCboeOptions_isRejected() {
        // Order Acknowledgment, byte 1 bit 2 = PegDifference (blank in the spec table)
        assertEquals("Invalid return bitfield for 0x25: byte 1 bit 2",
                ReturnBitfieldRules.validate(groups("06 00 81 25 01 02")));
    }

    @Test
    void bitfieldBeyondTheTable_isRejected() {
        assertEquals("Invalid return bitfield for 0x25: byte 20 bit 1",
                ReturnBitfieldRules.validate(groups("19 00 81 25 14 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 01")));
    }

    @Test
    void messageTypeWithoutReturnBitfields_isRejected() {
        assertEquals("Return bitfields not supported for message type 0x24",
                ReturnBitfieldRules.validate(groups("06 00 81 24 01 01")));
    }
}
