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
        java.util.Arrays.fill(b, (byte) 0x20);
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
    void parse_side() {
        // bit 7 = Side, plus OrderQty (bit 2)
        byte[] opt = new byte[5];
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(50);     // OrderQty (bit 2 first)
        ob.put((byte)'1'); // Side = Buy (bit 7)

        byte[] raw = buildRaw("C1", "O1", 1, new byte[]{(byte) 0x84}, opt); // 0x84 = bits 2,7
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);

        assertEquals((byte) '1', msg.getSide());
    }

    // ── Byte 2 optional fields ────────────────────────────────────────────────

    @Test
    void parse_byte2_fields_do_not_crash() {
        // Byte 2: 0x01=MaxFloor(4B), 0x02=StopPx(8B), 0x04=RoutingFirmID(4B)
        // Plus OrderQty (byte1 bit 2) to satisfy requirement
        byte[] opt = new byte[20]; // 4 + 4 + 8 + 4
        ByteBuffer ob = ByteBuffer.wrap(opt).order(ByteOrder.LITTLE_ENDIAN);
        ob.putInt(100);        // OrderQty (byte1 bit 2)
        ob.putInt(50);         // MaxFloor (byte2 bit 0)
        ob.putLong(100_000L);  // StopPx  (byte2 bit 1)
        ob.putInt(0);          // RoutingFirmID (byte2 bit 2)

        byte[] raw = buildRaw("C1", "O1", 2,
                new byte[]{0x04, 0x07}, opt); // byte1:OrderQty, byte2:MaxFloor+StopPx+RoutingFirmID

        assertDoesNotThrow(() -> ModifyOrderMessage.parse(raw));
        ModifyOrderMessage msg = ModifyOrderMessage.parse(raw);
        assertEquals(100, msg.getOrderQty());
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
}
