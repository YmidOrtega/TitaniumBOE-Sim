package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Phase 6 — Cancel Order wire format tests against spec v2.11.90 Table 36.
 */
class CancelOrderMessageTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static byte[] buildRaw(String origClOrdID, int numBitfields, byte[] bfBytes, byte[] optBytes) {
        // Fixed: SOM(2) + MsgLen(2) + Type(1) + MatchUnit(1) + SeqNum(4) + OrigClOrdID(20) + NumBF(1) = 31
        int totalSize = 31 + numBitfields + (optBytes != null ? optBytes.length : 0);
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.LITTLE_ENDIAN);
        buf.put((byte) 0xBA); buf.put((byte) 0xBA);
        buf.putShort((short) (totalSize - 2));
        buf.put((byte) 0x39);  // MessageType
        buf.put((byte) 0x00);  // MatchingUnit
        buf.putInt(0);         // SequenceNumber

        // OrigClOrdID: 20B NUL-padded
        byte[] oidBytes = new byte[20];
        if (origClOrdID != null && !origClOrdID.isEmpty()) {
            byte[] src = origClOrdID.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
            System.arraycopy(src, 0, oidBytes, 0, Math.min(src.length, 20));
        }
        buf.put(oidBytes);

        buf.put((byte) numBitfields);
        for (int i = 0; i < numBitfields && bfBytes != null && i < bfBytes.length; i++) buf.put(bfBytes[i]);
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
        // Alpha/Alphanumeric fields are NUL-padded per spec p.10
        putText(buf, s, len);
    }

    // ── Fixed header ─────────────────────────────────────────────────────────

    @Test
    void testMessageType() {
        CancelOrderMessage msg = new CancelOrderMessage("ORD001");
        assertEquals(0x39, msg.getMessageType() & 0xFF);
    }

    @Test
    void testSomBytes() {
        byte[] wire = new CancelOrderMessage("ORD001").toBytes();
        assertEquals((byte) 0xBA, wire[0]);
        assertEquals((byte) 0xBA, wire[1]);
    }

    @Test
    void testMessageTypeInWire() {
        byte[] wire = new CancelOrderMessage("ORD001").toBytes();
        assertEquals(0x39, wire[4] & 0xFF);
    }

    @Test
    void testFixedSizeNoBitfields() {
        // 31 bytes: fixed header with 0 bitfields
        byte[] wire = new CancelOrderMessage("ORD001").toBytes();
        assertEquals(31, wire.length);
    }

    @Test
    void testMessageLengthField() {
        byte[] wire = new CancelOrderMessage("ORD001").toBytes();
        int msgLen = ByteBuffer.wrap(wire, 2, 2).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;
        // MessageLength = totalSize - 2 (excludes SOM)
        assertEquals(wire.length - 2, msgLen);
    }

    // ── OrigClOrdID NUL-padding ───────────────────────────────────────────────

    @Test
    void testOrigClOrdIdNulPadded() {
        byte[] wire = new CancelOrderMessage("ORD001").toBytes();
        // OrigClOrdID starts at offset 10, length 20
        byte[] oid = Arrays.copyOfRange(wire, 10, 30);
        // First 6 bytes = "ORD001", remaining 14 = NUL
        assertEquals('O', oid[0]);
        assertEquals('R', oid[1]);
        assertEquals('D', oid[2]);
        assertEquals('0', oid[3]);
        assertEquals('0', oid[4]);
        assertEquals('1', oid[5]);
        for (int i = 6; i < 20; i++) {
            assertEquals(0x00, oid[i], "Expected NUL at offset " + i);
        }
    }

    @Test
    void testMassCancelOrigClOrdIdAllZero() {
        // empty OrigClOrdID = mass cancel; wire must have 20 NUL bytes
        CancelOrderMessage msg = new CancelOrderMessage("");
        byte[] wire = msg.toBytes();
        byte[] oid = Arrays.copyOfRange(wire, 10, 30);
        for (int i = 0; i < 20; i++) {
            assertEquals(0x00, oid[i], "Expected all-zero OrigClOrdID at index " + i);
        }
    }

    // ── isMassCancel ─────────────────────────────────────────────────────────

    @Test
    void testIsMassCancelTrue_empty() {
        assertTrue(new CancelOrderMessage("").isMassCancel());
    }

    @Test
    void testIsMassCancelTrue_null() {
        assertTrue(new CancelOrderMessage(null).isMassCancel());
    }

    @Test
    void testIsMassCancelFalse() {
        assertFalse(new CancelOrderMessage("ORD001").isMassCancel());
    }

    // ── Single-cancel round-trip ──────────────────────────────────────────────

    @Test
    void testSingleCancelRoundTrip() {
        byte[] raw = buildRaw("ORD-ABC-001", 0, null, null);
        CancelOrderMessage parsed = CancelOrderMessage.parse(raw);
        assertEquals("ORD-ABC-001", parsed.getOrigClOrdID());
        assertFalse(parsed.isMassCancel());
        assertArrayEquals(raw, parsed.toBytes());
    }

    // ── Bitfield 1 — ClearingFirm (0x01) ─────────────────────────────────────

    @Test
    void testBf1ClearingFirm() {
        // BF1 = 0x01 → ClearingFirm 4B Alpha (NUL-padded per spec p.10)
        ByteBuffer opt = ByteBuffer.allocate(4);
        putAlpha(opt, "FIRM", 4);
        byte[] raw = buildRaw("ORD1", 1, new byte[]{0x01}, opt.array());
        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("FIRM", msg.getClearingFirm());
        // round-trip
        assertArrayEquals(raw, msg.toBytes());
    }

    // ── Bitfield 1 — RiskRoot (0x08) ─────────────────────────────────────────

    @Test
    void testBf1RiskRoot_NulPadded() {
        // BF1 = 0x08 → RiskRoot 6B Text (NUL-padded)
        ByteBuffer opt = ByteBuffer.allocate(6);
        putText(opt, "SPX", 6);
        byte[] raw = buildRaw("ORD1", 1, new byte[]{0x08}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("SPX", msg.getRiskRoot());
        // verify NUL-padding in wire
        byte[] wire = msg.toBytes();
        // RiskRoot starts at: 31 (fixed) + 1 (bf array) = 32
        assertEquals(0x00, wire[32 + 3], "byte 3 of RiskRoot must be NUL");
        assertEquals(0x00, wire[32 + 4], "byte 4 of RiskRoot must be NUL");
        assertEquals(0x00, wire[32 + 5], "byte 5 of RiskRoot must be NUL");
    }

    // ── Bitfield 1 — MassCancelId (0x10) ─────────────────────────────────────

    @Test
    void testBf1MassCancelId_NulPadded() {
        // BF1 = 0x10 → MassCancelId 20B Text (NUL-padded)
        ByteBuffer opt = ByteBuffer.allocate(20);
        putText(opt, "MCID-12345", 20);
        byte[] raw = buildRaw("", 1, new byte[]{0x10}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("MCID-12345", msg.getMassCancelId());
        assertArrayEquals(raw, msg.toBytes());
    }

    // ── Bitfield 1 — RoutingFirmID (0x20) ────────────────────────────────────

    @Test
    void testBf1RoutingFirmId_NulPadded() {
        // BF1 = 0x20 → RoutingFirmID 4B Alpha (NUL-padded per spec p.10)
        ByteBuffer opt = ByteBuffer.allocate(4);
        putAlpha(opt, "RT01", 4);
        byte[] raw = buildRaw("ORD1", 1, new byte[]{0x20}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("RT01", msg.getRoutingFirmID());
        assertArrayEquals(raw, msg.toBytes());
    }

    // ── Bitfield 2 — MassCancelInst (0x01) ───────────────────────────────────

    @Test
    void testBf2MassCancelInst_NulPadded() {
        // BF2 = 0x01 → MassCancelInst 16B Text (NUL-padded)
        ByteBuffer opt = ByteBuffer.allocate(16);
        putText(opt, "F01L", 16);
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("F01L", msg.getMassCancelInst());
        // verify NUL at position 4
        byte[] wire = msg.toBytes();
        // MassCancelInst starts at: 31 (fixed) + 2 (bf array) = 33
        assertEquals(0x00, wire[33 + 4], "byte 4 of MassCancelInst must be NUL");
        assertArrayEquals(raw, wire);
    }

    // ── Blank bits and required SendTime ─────────────────────────────────────

    @Test
    void blankBitInTheSpecTable_isRejected() {
        // BF2 = 0x02 is Symbol on other Cboe platforms, blank on Titanium Options
        byte[] raw = buildRaw("ORD1", 2, new byte[]{0x00, 0x02}, new byte[8]);
        assertEquals("Bitfield 2 bit 2 cannot be specified on Cancel Order", CancelOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void massCancelLockoutAndMassCancelBits_areRejected() {
        byte[] raw = buildRaw("", 1, new byte[]{0x06}, new byte[]{'Y', 'Y'});
        assertEquals("Bitfield 1 bit 2 cannot be specified on Cancel Order", CancelOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void bitBeyondTheTwoSpecBitfields_isRejected() {
        byte[] raw = buildRaw("ORD1", 3, new byte[]{0x00, 0x08, 0x01}, new byte[9]);
        assertEquals("Bitfield 3 bit 1 cannot be specified on Cancel Order", CancelOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void missingSendTime_isRejected() {
        assertEquals("SendTime is required on Cancel Order", CancelOrderMessage.parse(buildRaw("ORD1", 0, null, null)).getFieldError());
    }

    @Test
    void optionalFieldsShorterThanTheBitfields_areRejected() {
        byte[] raw = buildRaw("ORD1", 2, new byte[]{0x00, 0x08}, new byte[4]);
        assertEquals("Cancel Order is too short for its optional fields", CancelOrderMessage.parse(raw).getFieldError());
    }

    @Test
    void restCancel_isNotParsed_soItHasNoFieldError() {
        assertNull(new CancelOrderMessage("ORD1").getFieldError());
    }

    // ── Spec examples ────────────────────────────────────────────────────────

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    @Test
    void specTable37Example_singleCancel() {
        CancelOrderMessage m = CancelOrderMessage.parse(hex("BA BA 2A 00 39 00 64 00 00 00 41 42 43 31 32 33 00 00 00 00 00 00 00 00 00 00 00 00 00 00 "
                + "02 01 08 54 45 53 54 E0 7A B9 DA 13 3B 42 16"));

        assertEquals("ABC123", m.getOrigClOrdID());
        assertFalse(m.isMassCancel());
        assertEquals("TEST", m.getClearingFirm());
        assertEquals(1_603_909_373_757_324_000L, m.getSendTime());
        assertNull(m.getFieldError());
    }

    @Test
    void specTable38Example_massCancel() {
        CancelOrderMessage m = CancelOrderMessage.parse(hex("BA BA 54 00 39 00 64 00 00 00 " + "00 ".repeat(20)
                + "02 19 09 54 45 53 54 4D 53 46 54 00 00 41 42 43 31 32 33 " + "00 ".repeat(14)
                + "46 53 4C 42 " + "00 ".repeat(12) + "E0 7A B9 DA 13 3B 42 16"));

        assertTrue(m.isMassCancel());
        assertEquals("TEST", m.getClearingFirm());
        assertEquals("MSFT", m.getRiskRoot());
        assertEquals("ABC123", m.getMassCancelId());
        assertEquals("FSLB", m.getMassCancelInst());
        assertEquals('F', m.massCancelInstChar(1));
        assertEquals('S', m.massCancelInstChar(2));
        assertEquals('L', m.massCancelInstChar(3));
        assertEquals('B', m.massCancelInstChar(4));
        assertNull(m.massCancelInstChar(5));
        assertNull(m.getFieldError());
    }

    // ── Bitfield 2 — SendTime (0x08) ──────────────────────────────────────────

    @Test
    void testBf2SendTime() {
        // BF2 = 0x08 → SendTime 8B Binary LE
        long ts = 1_748_000_000_000_000_000L;
        ByteBuffer opt = ByteBuffer.allocate(8).order(ByteOrder.LITTLE_ENDIAN);
        opt.putLong(ts);
        byte[] raw = buildRaw("ORD1", 2, new byte[]{0x00, 0x08}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals(ts, msg.getSendTime());
        assertArrayEquals(raw, msg.toBytes());
    }

    // ── Mass cancel full round-trip ───────────────────────────────────────────

    @Test
    void testMassCancelFullRoundTrip() {
        // BF1: 0x01=ClearingFirm(4) + 0x08=RiskRoot(6) + 0x10=MassCancelId(20) + 0x20=RoutingFirmID(4) = 0x39
        // BF2: 0x01=MassCancelInst(16) + 0x08=SendTime(8) = 0x09
        ByteBuffer opt = ByteBuffer.allocate(4 + 6 + 20 + 4 + 16 + 8);
        putAlpha(opt, "ABCD", 4);
        putText(opt, "SPX", 6);
        putText(opt, "MCID-XYZ-001-ABCD", 20);
        putAlpha(opt, "RT01", 4);
        putText(opt, "ASL", 16);
        opt.order(ByteOrder.LITTLE_ENDIAN).putLong(999_999_000_000_000L);

        byte[] raw = buildRaw("", 2, new byte[]{0x39, 0x09}, opt.array());
        CancelOrderMessage msg = CancelOrderMessage.parse(raw);

        assertTrue(msg.isMassCancel());
        assertEquals("ABCD", msg.getClearingFirm());
        assertEquals("SPX", msg.getRiskRoot());
        assertEquals("MCID-XYZ-001-ABCD", msg.getMassCancelId());
        assertEquals("RT01", msg.getRoutingFirmID());
        assertEquals("ASL", msg.getMassCancelInst());
        assertEquals(999_999_000_000_000L, msg.getSendTime());
        assertNull(msg.getFieldError());

        assertArrayEquals(raw, msg.toBytes());
    }

    @Test
    void settersSetTheirBits_soTheEncoderRoundTrips() {
        CancelOrderMessage msg = new CancelOrderMessage("");
        msg.setClearingFirm("TEST");
        msg.setMassCancelInst("FM");
        msg.setSendTime(42L);

        CancelOrderMessage parsed = CancelOrderMessage.parse(msg.toBytes());

        assertEquals("TEST", parsed.getClearingFirm());
        assertEquals('M', parsed.massCancelInstChar(2));
        assertEquals(42L, parsed.getSendTime());
        assertNull(parsed.getFieldError());
    }

    // ── Parse rejects bad input ───────────────────────────────────────────────

    @Test
    void testParseTooShortThrows() {
        assertThrows(IllegalArgumentException.class, () -> CancelOrderMessage.parse(new byte[10]));
    }

    @Test
    void testParseWrongTypeThrows() {
        byte[] raw = buildRaw("ORD1", 0, null, null);
        raw[4] = 0x38; // wrong type
        assertThrows(IllegalArgumentException.class, () -> CancelOrderMessage.parse(raw));
    }

    // ── Bitfield parsing order: interleaved BF1 bits ─────────────────────────

    @Test
    void testBf1MultipleFields_parseOrder() {
        // BF1 = 0x09 = 0x01(ClearingFirm 4B) + 0x08(RiskRoot 6B)
        // Fields must appear in ascending bit order in the wire: ClearingFirm first, then RiskRoot
        ByteBuffer opt = ByteBuffer.allocate(4 + 6);
        putAlpha(opt, "CFXX", 4);
        putText(opt, "NDX", 6);
        byte[] raw = buildRaw("ORD1", 1, new byte[]{0x09}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("CFXX", msg.getClearingFirm());
        assertEquals("NDX", msg.getRiskRoot());
        assertArrayEquals(raw, msg.toBytes());
    }
}
