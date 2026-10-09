package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class LoginRequestStructureTest {

    // Fixed part of the spec Table 14 example: SubID 0001, user TEST, password TESTING
    private static final String FIXED = "BA BA 00 00 37 00 00 00 00 00 30 30 30 31 54 45 53 54 54 45 53 54 49 4E 47 00 00 00";

    private static byte[] login(int numberOfGroups, String groupsHex) {
        byte[] fixed = hex(FIXED);
        byte[] groups = groupsHex.isBlank() ? new byte[0] : hex(groupsHex);
        byte[] msg = new byte[fixed.length + 1 + groups.length];
        System.arraycopy(fixed, 0, msg, 0, fixed.length);
        msg[fixed.length] = (byte) numberOfGroups;
        System.arraycopy(groups, 0, msg, fixed.length + 1, groups.length);
        ByteBuffer.wrap(msg).order(ByteOrder.LITTLE_ENDIAN).putShort(2, (short) (msg.length - 2));
        return msg;
    }

    private static byte[] hex(String s) {
        String[] p = s.trim().split("\\s+");
        byte[] b = new byte[p.length];
        for (int i = 0; i < p.length; i++) b[i] = (byte) Integer.parseInt(p[i], 16);
        return b;
    }

    private static String rejection(byte[] msg) {
        return assertThrows(IllegalArgumentException.class, () -> LoginRequestMessage.parseFromBytes(msg)).getMessage();
    }

    @Test
    void specTable14Example_parsesEveryField() {
        LoginRequestMessage m = LoginRequestMessage.parseFromBytes(login(3,
                "0F 00 80 01 02 01 4A BB 01 00 02 00 00 00 00 "
                + "08 00 81 25 03 00 41 05 "
                + "0B 00 81 2C 06 00 41 07 00 40 00"));

        assertEquals("0001", m.getSessionSubID());
        assertEquals("TEST", m.getUsername());
        assertEquals("TESTING", m.getPassword());
        assertEquals(Map.of(1, 113_482, 2, 0), m.getUnitSequences().lastReceivedByUnit());
        assertArrayEquals(new byte[]{0x00, 0x41, 0x05}, m.getReturnBitfields().maskFor((byte) 0x25));
        assertArrayEquals(new byte[]{0x00, 0x41, 0x07, 0x00, 0x40, 0x00}, m.getReturnBitfields().maskFor((byte) 0x2C));
    }

    @Test
    void groupOrder_andUnknownGroupTypes_doNotMatter() {
        LoginRequestMessage m = LoginRequestMessage.parseFromBytes(login(3,
                "06 00 99 AA BB CC "
                + "08 00 81 25 03 00 41 05 "
                + "0A 00 80 00 01 01 05 00 00 00"));

        assertArrayEquals(new byte[]{0x00, 0x41, 0x05}, m.getReturnBitfields().maskFor((byte) 0x25));
        assertEquals(Map.of(1, 5), m.getUnitSequences().lastReceivedByUnit());
        assertEquals(3, m.getNumberOfParamGroups(), "The echoed count must match the echoed bytes, unknown groups included");
        assertEquals(24, m.getParamGroupBytes().length);
    }

    @Test
    void duplicateReturnBitfieldsForTheSameMessageType_areRejected() {
        assertEquals("Duplicate Return Bitfields group for message type 0x25",
                rejection(login(2, "06 00 81 25 01 00 06 00 81 25 01 01")));
    }

    @Test
    void noUnspecifiedUnitReplayOtherThanZeroOrOne_isRejected() {
        assertEquals("NoUnspecifiedUnitReplay must be 0x00 or 0x01, got 0x02",
                rejection(login(1, "05 00 80 02 00")));
    }

    @Test
    void sameUnitTwiceInUnitSequences_isRejected() {
        assertEquals("Unit 1 appears twice in the Unit Sequences group",
                rejection(login(1, "0F 00 80 00 02 01 05 00 00 00 01 06 00 00 00")));
    }

    @Test
    void tooShort_isRejected() {
        assertTrue(rejection(hex("BA BA 08 00 37 00 00 00 00 00")).contains("shorter"));
    }

    @Test
    void twoUnitSequencesGroups_areRejected() {
        String msg = rejection(login(2, "05 00 80 00 00 05 00 80 01 00"));
        assertEquals("Only one Unit Sequences parameter group may be included", msg);
    }

    @Test
    void groupLongerThanTheMessage_isRejected() {
        assertTrue(rejection(login(1, "20 00 81 25 01 00")).contains("exceeds the message"));
    }

    @Test
    void returnBitfieldsLengthNotMatchingItsCount_isRejected() {
        assertTrue(rejection(login(1, "09 00 81 25 03 00 41 05 FF")).contains("does not match 3 bitfields"));
    }

    @Test
    void unitSequencesLengthNotMatchingItsCount_isRejected() {
        assertTrue(rejection(login(1, "0A 00 80 00 02 01 05 00 00 00")).contains("does not match 2 units"));
    }

    @Test
    void fewerGroupsThanDeclared_isRejected() {
        assertTrue(rejection(login(2, "08 00 81 25 03 00 41 05")).contains("Parameter group 2 of 2 is missing"));
    }

    @Test
    void trailingBytesAfterTheGroups_areRejected() {
        assertTrue(rejection(login(1, "08 00 81 25 03 00 41 05 00 00")).contains("2 unexpected bytes"));
    }

    @Test
    void returnBitfieldsParse_skipsToTheDeclaredEndOfEachGroup() {
        ByteBuffer buf = ByteBuffer.wrap(hex("09 00 81 25 03 00 41 05 FF 08 00 81 2C 03 00 41 07")).order(ByteOrder.LITTLE_ENDIAN);

        ReturnBitfields parsed = ReturnBitfields.parse(2, buf);

        assertArrayEquals(new byte[]{0x00, 0x41, 0x05}, parsed.maskFor((byte) 0x25));
        assertArrayEquals(new byte[]{0x00, 0x41, 0x07}, parsed.maskFor((byte) 0x2C), "The group after an over-long one is not lost");
    }

    @Test
    void credentialsMustBeAlphanumeric() {
        byte[] badPassword = login(0, "");
        badPassword[24] = '!';
        byte[] badUser = login(0, "");
        badUser[15] = '_';
        byte[] badSubId = login(0, "");
        badSubId[10] = ' ';

        assertEquals("Invalid character 0x21 in Password (A-Z, a-z, 0-9)", rejection(badPassword));
        assertEquals("Invalid character 0x5F in Username (A-Z, a-z, 0-9)", rejection(badUser));
        assertEquals("Invalid character 0x20 in SessionSubID (A-Z, a-z, 0-9)", rejection(badSubId));
    }
}
