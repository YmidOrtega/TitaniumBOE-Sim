package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MassCancelAcknowledgmentMessageTest {

    @Test
    void layoutMatchesTable110() {
        byte[] b = new MassCancelAcknowledgmentMessage("ABC123", 99).toBytes();
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(44, b.length);
        assertEquals((byte) 0xBA, b[0]);
        assertEquals((byte) 0xBA, b[1]);
        assertEquals(42, buf.getShort(2), "Table 111 says 41, but the field offsets add up to 42");
        assertEquals(0x36, b[4]);
        assertEquals(0, b[5], "Unsequenced: MatchingUnit 0");
        assertEquals(0, buf.getInt(6), "Unsequenced: SequenceNumber 0");
        assertTrue(buf.getLong(10) > 0, "TransactionTime");
        assertArrayEquals(new byte[]{'A', 'B', 'C', '1', '2', '3', 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
                java.util.Arrays.copyOfRange(b, 18, 38));
        assertEquals(99, buf.getInt(38));
        assertEquals(0, b[42], "ReservedInternal");
        assertEquals(0, b[43], "SourceMatchingUnit");
    }

    @Test
    void roundTrips() {
        MassCancelAcknowledgmentMessage original = new MassCancelAcknowledgmentMessage("MC-1", 3);
        MassCancelAcknowledgmentMessage parsed = MassCancelAcknowledgmentMessage.fromBytes(original.toBytes());

        assertEquals("MC-1", parsed.getMassCancelId());
        assertEquals(3, parsed.getCancelledOrderCount());
        assertEquals(original.getTransactTime(), parsed.getTransactTime());
    }
}
