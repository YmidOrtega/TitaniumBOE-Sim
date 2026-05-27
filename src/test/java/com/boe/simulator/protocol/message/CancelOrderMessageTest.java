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

    // ── Bitfield 2 — Symbol (0x02) ────────────────────────────────────────────

    @Test
    void testBf2Symbol_NulPadded() {
        // BF2 = 0x02 → Symbol 8B Alphanumeric (NUL-padded per spec p.10)
        ByteBuffer opt = ByteBuffer.allocate(8);
        putAlpha(opt, "SPX", 8);
        byte[] raw = buildRaw("ORD1", 2, new byte[]{0x00, 0x02}, opt.array());

        CancelOrderMessage msg = CancelOrderMessage.parse(raw);
        assertEquals("SPX", msg.getSymbol());
        // verify NUL-padding in wire
        byte[] wire = msg.toBytes();
        // Symbol starts at: 31 (fixed) + 2 (bf array) = 33
        assertEquals(0x00, wire[33 + 3], "byte 3 of Symbol must be NUL");
        assertArrayEquals(raw, wire);
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
        // BF1=0x99 (ClearingFirm+RiskRoot+RoutingFirmID+OperatorId), BF2=0x09 (MassCancelInst+SendTime)
        // BF1: 0x01=ClearFirm(4) + 0x08=RiskRoot(6) + 0x10=MassCancelId(20) + 0x80=OperatorId(4) = 0x99
        byte bf1 = (byte) 0x99;
        byte bf2 = (byte) 0x09; // 0x01=MassCancelInst(16) + 0x08=SendTime(8)

        ByteBuffer opt = ByteBuffer.allocate(4 + 6 + 20 + 4 + 16 + 8);
        putAlpha(opt, "ABCD", 4);  // ClearingFirm
        putText(opt, "SPX", 6);    // RiskRoot
        putText(opt, "MCID-XYZ-001-ABCD", 20); // MassCancelId
        putAlpha(opt, "OPR1", 4);  // OperatorId
        putText(opt, "A01L", 16);  // MassCancelInst
        opt.order(ByteOrder.LITTLE_ENDIAN).putLong(999_999_000_000_000L); // SendTime

        byte[] raw = buildRaw("", 2, new byte[]{bf1, bf2}, opt.array());
        CancelOrderMessage msg = CancelOrderMessage.parse(raw);

        assertTrue(msg.isMassCancel());
        assertEquals("ABCD", msg.getClearingFirm());
        assertEquals("SPX", msg.getRiskRoot());
        assertEquals("MCID-XYZ-001-ABCD", msg.getMassCancelId());
        assertEquals("OPR1", msg.getOperatorId());
        assertEquals("A01L", msg.getMassCancelInst());
        assertEquals(999_999_000_000_000L, msg.getSendTime());

        assertArrayEquals(raw, msg.toBytes());
    }

    // ── MassCancelType ────────────────────────────────────────────────────────

    @Test
    void testMassCancelType_firm() {
        byte[] opt = new byte[16]; opt[0] = 'F';
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt);
        assertEquals(CancelOrderMessage.MassCancelType.FIRM, CancelOrderMessage.parse(raw).getMassCancelType());
    }

    @Test
    void testMassCancelType_symbol() {
        byte[] opt = new byte[16]; opt[0] = 'S';
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt);
        assertEquals(CancelOrderMessage.MassCancelType.SYMBOL, CancelOrderMessage.parse(raw).getMassCancelType());
    }

    @Test
    void testMassCancelType_all() {
        byte[] opt = new byte[16]; opt[0] = 'A';
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt);
        assertEquals(CancelOrderMessage.MassCancelType.ALL, CancelOrderMessage.parse(raw).getMassCancelType());
    }

    @Test
    void testMassCancelType_none_whenNoInst() {
        byte[] raw = buildRaw("", 0, null, null);
        assertEquals(CancelOrderMessage.MassCancelType.NONE, CancelOrderMessage.parse(raw).getMassCancelType());
    }

    // ── isLockoutRequested ────────────────────────────────────────────────────

    @Test
    void testIsLockoutRequested_true() {
        // MassCancelInst where char[2] == 'L'
        byte[] opt = new byte[16]; opt[0] = 'F'; opt[1] = '0'; opt[2] = 'L';
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt);
        assertTrue(CancelOrderMessage.parse(raw).isLockoutRequested());
    }

    @Test
    void testIsLockoutRequested_false() {
        byte[] opt = new byte[16]; opt[0] = 'F'; opt[1] = '0'; opt[2] = 'N';
        byte[] raw = buildRaw("", 2, new byte[]{0x00, 0x01}, opt);
        assertFalse(CancelOrderMessage.parse(raw).isLockoutRequested());
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

    @Test
    void testBf1_MassCancelLockoutAndMassCancel() {
        // BF1 = 0x06 = 0x02(MassCancelLockout 1B) + 0x04(MassCancel 1B)
        byte[] opt = new byte[]{(byte) 'Y', (byte) 'Y'};
        byte[] raw = buildRaw("", 1, new byte[]{0x06}, opt);
        // Should parse without throwing (fields are bytes, not exposed via getters for now)
        assertDoesNotThrow(() -> CancelOrderMessage.parse(raw));
        // round-trip
        assertArrayEquals(raw, CancelOrderMessage.parse(raw).toBytes());
    }
}
