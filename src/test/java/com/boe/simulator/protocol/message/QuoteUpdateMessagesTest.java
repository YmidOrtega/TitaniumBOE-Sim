package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class QuoteUpdateMessagesTest {

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    @Test
    void quoteUpdateRejected_specTable78Example_parses() {
        QuoteUpdateRejectedMessage m = QuoteUpdateRejectedMessage.fromBytes(hex(
                "BA BA 32 00 58 00 00 00 00 00 E0 FA 20 F7 36 71 F8 11 "
                + "41 42 43 31 32 33 00 00 00 00 00 00 00 00 00 00 4D "
                + "00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00"));

        assertEquals("ABC123", m.getQuoteUpdateID());
        assertEquals('M', m.getQuoteRejectReason());
        assertEquals(1_294_909_373_757_324_000L, m.getTransactTime());
    }

    @Test
    void quoteUpdateRejected_layoutMatchesTable77() {
        byte[] b = new QuoteUpdateRejectedMessage("ABC123", QuoteUpdateRejectedMessage.REASON_NOT_ENABLED_FOR_QUOTES).toBytes();
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(52, b.length);
        assertEquals(50, buf.getShort(2), "MessageLength excludes the two StartOfMessage bytes");
        assertEquals(0x58, b[4]);
        assertEquals(0, b[5], "MatchingUnit 0: unsequenced");
        assertEquals(0, buf.getInt(6), "SequenceNumber 0: unsequenced");
        assertEquals('A', b[18]);
        assertEquals(0, b[24], "QuoteUpdateID is NUL-padded");
        assertEquals('F', b[34]);
        for (int i = 35; i < 52; i++) assertEquals(0, b[i], "Reserved byte " + i);
    }

    @Test
    void quoteUpdate_readsHeaderAndQuoteUpdateID_forBothFormats() {
        for (byte type : new byte[]{0x55, 0x59}) {
            ByteBuffer buf = ByteBuffer.allocate(40).order(ByteOrder.LITTLE_ENDIAN);
            buf.put((byte) 0xBA).put((byte) 0xBA).putShort((short) 38).put(type).put((byte) 0).putInt(7);
            buf.put("Q1".getBytes());

            QuoteUpdateMessage m = QuoteUpdateMessage.parse(buf.array());

            assertEquals(type, m.getMessageType());
            assertEquals(7, m.getSequenceNumber());
            assertEquals("Q1", m.getQuoteUpdateID());
        }
    }

    @Test
    void quoteUpdate_shorterThanItsHeader_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> QuoteUpdateMessage.parse(new byte[20]));
    }
}
