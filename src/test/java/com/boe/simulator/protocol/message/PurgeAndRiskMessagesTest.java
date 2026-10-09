package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class PurgeAndRiskMessagesTest {

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    private static String zeros(int n) {
        return String.join(" ", java.util.Collections.nCopies(n, "00"));
    }

    private static final String SEND_TIME = "E0 7A B9 DA 13 3B 42 16";

    @Test
    void purgeOrders_specTable51Example_withCustomGroupIdsAndLockout() {
        PurgeOrdersMessage m = PurgeOrdersMessage.parse(hex("BA BA 41 00 47 00 64 00 00 00 00 02 15 40 02 BF BE C0 BE "
                + "54 45 53 54 46 53 4C 42 " + zeros(12) + " 41 42 43 31 32 33 " + zeros(14) + " " + SEND_TIME));

        assertNull(m.getFieldError());
        assertEquals(100, m.getSequenceNumber());
        assertEquals(List.of(48831, 48832), m.getCustomGroupIds());
        assertEquals("TEST", m.getClearingFirm());
        assertEquals("FSLB", m.getMassCancelInst());
        assertEquals('L', m.massCancelInstChar(3));
        assertEquals("ABC123", m.getMassCancelId());
        assertEquals(1_603_909_373_757_324_000L, m.getSendTime());
    }

    @Test
    void purgeOrders_specTable52Example_withRiskRoot() {
        PurgeOrdersMessage m = PurgeOrdersMessage.parse(hex("BA BA 43 00 47 00 64 00 00 00 00 02 1D 40 00 "
                + "54 45 53 54 46 53 4E 42 " + zeros(12) + " 41 42 43 00 00 00 41 42 43 31 32 33 " + zeros(14) + " " + SEND_TIME));

        assertNull(m.getFieldError());
        assertEquals("ABC", m.getRiskRoot());
        assertTrue(m.getCustomGroupIds().isEmpty());
    }

    @Test
    void purgeOrders_blankBitsTooManyGroupsAndMissingSendTime_areFieldErrors() {
        assertEquals("Bitfield 1 bit 2 cannot be specified on Purge Orders",
                PurgeOrdersMessage.parse(hex("BA BA 0F 00 47 00 01 00 00 00 00 01 02 00 00")).getFieldError());
        assertEquals("CustomGroupIDCnt must be between 0 and 10",
                PurgeOrdersMessage.parse(hex("BA BA 0B 00 47 00 01 00 00 00 00 00 0B")).getFieldError());
        assertEquals("SendTime is required on Purge Orders",
                PurgeOrdersMessage.parse(hex("BA BA 0B 00 47 00 01 00 00 00 00 00 00")).getFieldError());
    }

    @Test
    void purgeOrders_encoderRoundTrips() {
        PurgeOrdersMessage out = new PurgeOrdersMessage();
        out.setCustomGroupIds(List.of(7));
        out.setClearingFirm("TEST");
        out.setMassCancelInst("FSL");
        out.setMassCancelId("P1");
        out.setSendTime(5L);
        out.setTargetMatchingUnit(1);

        PurgeOrdersMessage in = PurgeOrdersMessage.parse(out.toBytes());

        assertNull(in.getFieldError());
        assertEquals(List.of(7), in.getCustomGroupIds());
        assertEquals("P1", in.getMassCancelId());
        assertEquals(1, in.getTargetMatchingUnit());
    }

    @Test
    void resetRisk_specTables54And55() {
        ResetRiskMessage symbolReset = ResetRiskMessage.parse(hex("BA BA 30 00 56 00 64 00 00 00 41 42 43 31 32 33 " + zeros(10)
                + " 53 46 00 00 00 00 00 00 00 00 00 00 54 45 53 54 41 42 43 00 00 00 00 00"));
        ResetRiskMessage unitReset = ResetRiskMessage.parse(hex("BA BA 30 00 56 00 64 00 00 00 41 42 43 31 32 33 " + zeros(10)
                + " 53 46 00 00 00 00 00 00 1A 00 00 00 54 45 53 54 00 00 00 00 00 00 00 00"));

        assertEquals("ABC123", symbolReset.getRiskStatusID());
        assertEquals("SF", symbolReset.getRiskReset());
        assertEquals("TEST", symbolReset.getClearingFirm());
        assertEquals("ABC", symbolReset.getRiskRoot());
        assertEquals(0, symbolReset.getCustomGroupId());
        assertNull(symbolReset.getFieldError());
        assertEquals(26, unitReset.getTargetMatchingUnit());
        assertEquals("", unitReset.getRiskRoot());
    }

    @Test
    void riskResetAcknowledgment_layoutMatchesTable108() {
        byte[] b = new RiskResetAcknowledgmentMessage("ABC123", RiskResetAcknowledgmentMessage.RESULT_SUCCESS).toBytes();

        assertEquals(27, b.length);
        assertEquals(25, ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN).getShort(2), "Table 109 MessageLength 25");
        assertEquals(0x57, b[4]);
        assertEquals('Y', b[26], "Table 109 shows 00 with the note Y = Success; Y is 0x59");
        assertEquals("ABC123", RiskResetAcknowledgmentMessage.fromBytes(b).getRiskStatusID());
    }

    @Test
    void purgeRejected_specTable107Example() {
        String text = "41 44 4D 49 4E " + zeros(55);
        PurgeRejectedMessage m = PurgeRejectedMessage.fromBytes(hex("BA BA 72 00 48 00 00 00 00 00 E0 FA 20 F7 36 71 F8 11 41 "
                + text + " 00 0F " + zeros(14) + " 08 54 45 53 54 " + zeros(16)));

        assertEquals('A', m.getPurgeRejectReason());
        assertEquals("ADMIN", m.getText());
        assertEquals("TEST", m.getReturnFields().get(ReturnField.MASS_CANCEL_ID));
    }

    @Test
    void purgeNotification_layoutMatchesTable112() {
        byte[] b = new PurgeNotificationMessage("ABC123", 99, 3, "TEST", "MSFT", true).toBytes();
        ByteBuffer buf = ByteBuffer.wrap(b).order(ByteOrder.LITTLE_ENDIAN);

        assertEquals(56, b.length);
        assertEquals(54, buf.getShort(2), "Table 113 shows 56, the total size; MessageLength excludes the two StartOfMessage bytes");
        assertEquals(0x63, b[4]);
        assertEquals(99, buf.getInt(38));
        assertEquals(3, b[42]);
        assertEquals('Y', b[53], "Table 113 shows 31 with the note Y = lockout");
        assertEquals(0, b[55], "No return field can be requested for Purge Notification (p.193)");

        PurgeNotificationMessage parsed = PurgeNotificationMessage.fromBytes(b);
        assertEquals("MSFT", parsed.getRiskRoot());
        assertTrue(parsed.isLockout());
    }

    @Test
    void massCancelAcknowledgment_carriesTheSourceMatchingUnit() {
        assertEquals(1, MassCancelAcknowledgmentMessage.fromBytes(new MassCancelAcknowledgmentMessage("ID", 2, 1).toBytes()).getSourceMatchingUnit());
    }
}
