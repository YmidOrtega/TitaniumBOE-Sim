package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 9 — Modify Order wire format tests against spec v2.11.90 Table 39.
 */
class ModifyOrderMessageTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    /**
     * Builds a raw Modify Order wire buffer.
     * Fixed section: SOM(2) + MsgLen(2) + Type(1) + MU(1) + SeqNum(4)
     *              + ClOrdID(20) + OrigClOrdID(20) + NumBF(1) = 51 bytes
     */
    private static byte[] buildRaw(String clOrdID, String origClOrdID,
                                    int numBitfields, byte[] bfBytes, byte[] optBytes) {
        int totalSize = 51 + numBitfields + (optBytes != null ? optBytes.length : 0);
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);

        buf.put((byte) 0xBA); buf.put((byte) 0xBA);            // SOM
        buf.putShort((short) (totalSize - 2));                  // MessageLength
        buf.put(ModifyOrderMessage.MESSAGE_TYPE);               // 0x3A
        buf.put((byte) 0x00);                                   // MatchingUnit
        buf.putInt(100);                                        // SequenceNumber

        putText(buf, clOrdID, 20);
        putText(buf, origClOrdID, 20);

        buf.put((byte) numBitfields);
        for (int i = 0; i < numBitfields && bfBytes != null && i < bfBytes.length; i++) {
            buf.put(bfBytes[i]);
        }
        if (optBytes != null) buf.put(optBytes);

        return buf.array();
    }

    private static void putText(ByteBuffer buf, String s, int len) {
        byte[] b = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, b, 0, Math.min(src.length, len));
        }
        buf.put(b);
    }

    private static void putAlpha(ByteBuffer buf, String s, int len) {
        byte[] b = new byte[len];
        if (s != null && !s.isEmpty()) {
            byte[] src = s.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, b, 0, Math.min(src.length, len));
        }
        buf.put(b);
    }

    private static byte[] priceBytes(long rawPrice) {
        ByteBuffer b = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        b.putLong(rawPrice);
        return b.array();
    }

    private static byte[] qtyBytes(int qty) {
        ByteBuffer b = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN);
        b.putInt(qty);
        return b.array();
    }

    // ── Spec example (Table 40, p.78) ────────────────────────────────────────

    @Test
    void specExample_Table40() {
        // MessageLength = 0x3E = 62 → total = 64
        // ClOrdID = ABC124, OrigClOrdID = ABC123
        // NumberOfBitfields = 1, Bitfield1 = 0x0C (OrderQty + Price)
        // OrderQty = 100, Price = 12.34
        byte[] opt = new byte[12];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(100);               // OrderQty
        ob.putLong(123_400L);         // Price = 12.34 × 10000 = 123400

        byte[] raw = buildRaw("ABC124", "ABC123", 1, new byte[]{0x0C}, opt);
        assertEquals(64, raw.length);

        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals("ABC124", msg.getClOrdID());
        assertEquals("ABC123", msg.getOrigClOrdID());
        assertEquals(100, msg.getOrderQty());
        assertEquals(new BigDecimal("12.3400"), msg.getPrice());
        assertTrue(msg.hasOrderQty());
        assertTrue(msg.hasPrice());
    }

    // ── Header fields ─────────────────────────────────────────────────────────

    @Test
    void messageType_is_0x3A() {
        assertEquals(0x3A, ModifyOrderMessage.MESSAGE_TYPE);
    }

    @Test
    void parse_header_fields() {
        byte[] raw = buildRaw("NEW1", "OLD1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals((byte) 0x00, msg.getMatchingUnit());
        assertEquals(100, msg.getSequenceNumber());
    }

    @Test
    void parse_clOrdIDs() {
        byte[] raw = buildRaw("NEWORDER1", "ORIGORD1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals("NEWORDER1", msg.getClOrdID());
        assertEquals("ORIGORD1", msg.getOrigClOrdID());
    }

    // ── Required optional fields ──────────────────────────────────────────────

    @Test
    void parse_orderQty_only() {
        byte[] opt = qtyBytes(500);
        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x04}, opt);  // bit 2 = OrderQty
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals(500, msg.getOrderQty());
        assertTrue(msg.hasOrderQty());
        assertFalse(msg.hasPrice());
    }

    @Test
    void parse_price_only() {
        byte[] opt = priceBytes(60_000L); // $6.0000
        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x08}, opt);  // bit 3 = Price
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertFalse(msg.hasOrderQty());
        assertTrue(msg.hasPrice());
        assertEquals(new BigDecimal("6.0000"), msg.getPrice());
    }

    @Test
    void parse_orderQty_and_price() {
        byte[] opt = new byte[12];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(200);         // OrderQty
        ob.putLong(150_000L);   // Price = 15.0000

        byte[] raw = buildRaw("C2", "O2", 1, new byte[]{0x0C}, opt);  // bits 2,3
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals(200, msg.getOrderQty());
        assertEquals(new BigDecimal("15.0000"), msg.getPrice());
    }

    // ── Optional fields ───────────────────────────────────────────────────────

    @Test
    void parse_ordType() {
        // bit 4 = OrdType; also include OrderQty (bit 2) to be realistic
        byte[] opt = new byte[5];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(100);    // OrderQty (bit 2)
        ob.put((byte)'2'); // OrdType = Limit (bit 4)

        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x14}, opt); // 0x14 = bits 2,4
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals((byte) '2', msg.getOrdType());
    }

    @Test
    void parse_clearingFirm() {
        // bit 0 = ClearingFirm (4B Alpha), bit 2 = OrderQty
        byte[] opt = new byte[8];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        putAlpha(ob, "ABCD", 4); // ClearingFirm (bit 0 comes first)
        ob.putInt(100);           // OrderQty (bit 2)

        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x05}, opt); // 0x05 = bits 0,2
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals("ABCD", msg.getClearingFirm());
        assertEquals(100, msg.getOrderQty());
    }

    @Test
    void side_isNotAllowed() {
        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{(byte) 0x84}, new byte[]{50, 0, 0, 0, '1'});
        assertEquals("Bitfield 1 bit 128 cannot be specified on Modify Order", ModifyOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void reservedBit_isRejected() {
        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x06}, new byte[8]);
        assertEquals("Bitfield 1 bit 2 cannot be specified on Modify Order", ModifyOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void blankAndNotAllowedBitsOfBitfield2_areRejected() {
        for (int bit : new int[]{0x08, 0x10, 0x20, 0x40, 0x80}) {
            byte[] raw = buildRaw("C1", "O1", 2, new byte[]{0x04, (byte) bit}, new byte[24]);
            assertEquals("Bitfield 2 bit " + bit + " cannot be specified on Modify Order",
                    ModifyOrderMessage.parse(raw).getFieldError());
        }
    }

    @Test
    void bitBeyondTheTwoSpecBitfields_isRejected() {
        byte[] raw = buildRaw("C1", "O1", 3, new byte[]{0x04, 0x00, 0x01}, new byte[5]);
        assertEquals("Bitfield 3 bit 1 cannot be specified on Modify Order", ModifyOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void optionalFieldsShorterThanTheBitfields_areRejected() {
        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{0x0C}, new byte[6]);
        assertEquals("Modify Order is too short for its optional fields", ModifyOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void routingFirmId_isRead_andByte2DefaultsAreAccepted() {
        ByteBuffer ob = ByteBuffer.allocate(20).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(100);        // OrderQty
        ob.putInt(0);          // MaxFloor
        ob.putLong(0);         // StopPx
        putAlpha(ob, "RTFM", 4);

        ModifyOrderMessage msg = ModifyOrderMessage.parse(buildRaw("C1", "O1", 2, new byte[]{0x04, 0x07}, ob.array()));

        assertEquals(100, msg.getOrderQty());
        assertEquals("RTFM", msg.getRoutingFirmID());
        assertNull(msg.getFieldError());
    }

    @Test
    void maxFloorAndStopPx_areRead() {
        ByteBuffer maxFloor = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN).putInt(100).putInt(50);
        ModifyOrderMessage withMaxFloor = ModifyOrderMessage.parse(buildRaw("C1", "O1", 2, new byte[]{0x04, 0x01}, maxFloor.array()));
        assertNull(withMaxFloor.getFieldError());
        assertEquals(50, withMaxFloor.getMaxFloor());

        ByteBuffer stopPx = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN).putInt(100).putLong(100_000L);
        ModifyOrderMessage withStopPx = ModifyOrderMessage.parse(buildRaw("C1", "O1", 2, new byte[]{0x04, 0x02}, stopPx.array()));
        assertNull(withStopPx.getFieldError());
        assertEquals(new BigDecimal("10.0000"), withStopPx.getStopPx());

        assertEquals("ExecInst is not supported by the simulator",
                ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x44}, new byte[]{100, 0, 0, 0, 'f'})).getFieldError());
        assertNull(ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x44}, new byte[]{100, 0, 0, 0, 0})).getFieldError());
    }

    @Test
    void cancelOrigOnReject_isRead_andValidated() {
        ModifyOrderMessage yes = ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x24}, new byte[]{100, 0, 0, 0, 'Y'}));
        ModifyOrderMessage no = ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x24}, new byte[]{100, 0, 0, 0, 'N'}));
        ModifyOrderMessage bad = ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x24}, new byte[]{100, 0, 0, 0, 'X'}));

        assertTrue(yes.cancelsOrigOnReject());
        assertNull(yes.getFieldError());
        assertFalse(no.cancelsOrigOnReject());
        assertEquals("Invalid CancelOrigOnReject 'X'", bad.getFieldError());
    }

    @Test
    void orderQtyZero_isPresent() {
        ModifyOrderMessage msg = ModifyOrderMessage.parse(buildRaw("C1", "O1", 1, new byte[]{0x04}, new byte[4]));
        assertTrue(msg.hasOrderQty(), "OrderQty 0 is present: the delta cancels the order");
    }

    // ── Validation ────────────────────────────────────────────────────────────

    @Test
    void parse_throws_on_null_data() {
        assertThrows(IllegalArgumentException.class, () -> ModifyOrderMessage.parse(null));
    }

    @Test
    void parse_throws_on_too_short() {
        assertThrows(IllegalArgumentException.class,
                () -> ModifyOrderMessage.parse(new byte[50]));
    }

    @Test
    void hasOrderQty_false_when_not_present() {
        byte[] raw = buildRaw("C1", "O1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);
        assertFalse(msg.hasOrderQty());
        assertEquals(0, msg.getOrderQty());
    }

    @Test
    void hasPrice_false_when_not_present() {
        byte[] raw = buildRaw("C1", "O1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);
        assertFalse(msg.hasPrice());
        assertNull(msg.getPrice());
    }

    @Test
    void toString_contains_key_fields() {
        byte[] opt = new byte[12];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(100);
        ob.putLong(123_400L);

        byte[] raw = buildRaw("NEW01", "OLD01", 1, new byte[]{0x0C}, opt);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        String s = msg.toString();
        assertTrue(s.contains("NEW01"));
        assertTrue(s.contains("OLD01"));
    }

    @Test
    void toBytes_throws_unsupported() {
        byte[] raw = buildRaw("C1", "O1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);
        assertThrows(UnsupportedOperationException.class, msg::toBytes);
    }

    // ── ApplicationMessage hierarchy ──────────────────────────────────────────

    @Test
    void is_application_message() {
        byte[] raw = buildRaw("C1", "O1", 0, null, null);
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);
        assertInstanceOf(ApplicationMessage.class, msg);
        assertInstanceOf(BoeProtocolMessage.class, msg);
    }

    // ── Character sets ────────────────────────────────────────────────────────

    @Test
    void clOrdIDWithAComma_isRejected() {
        ByteBuffer ob = ByteBuffer.allocate(12).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(10);
        ob.putLong(1_000_000L);
        byte[] raw = buildRaw("NEW,1", "ORIG1", 1, new byte[]{0x0C}, ob.array());

        assertEquals("Invalid character 0x2C in ClOrdID (33-126 except ,;|@\")",
                ModifyOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void spacePaddedClearingFirm_isRejected() {
        ByteBuffer ob = ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN);
        ob.put(new byte[]{'A', 'B', ' ', ' '});
        ob.putInt(10);
        ob.putLong(1_000_000L);
        byte[] raw = buildRaw("NEW1", "ORIG1", 1, new byte[]{0x0D}, ob.array());

        assertEquals("Invalid character 0x20 in ClearingFirm (A-Z, a-z)", ModifyOrderMessage.parse(raw).getFieldError());
    }
}
