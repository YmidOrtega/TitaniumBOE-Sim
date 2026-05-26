package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LogoutResponseMessageTest {

    @Test
    void constructor_shouldSetProperties_whenGivenValidArguments() {
        byte reason = LogoutResponseMessage.REASON_USER_REQUESTED;
        String text = "User requested logout";
        int lastReceivedSeq = 10;
        int numUnits = 1;
        byte matchingUnit = 2;
        int sequenceNumber = 54321;

        LogoutResponseMessage message = new LogoutResponseMessage(reason, text, lastReceivedSeq, numUnits);
        message.setMatchingUnit(matchingUnit);
        message.setSequenceNumber(sequenceNumber);

        assertEquals(reason, message.getLogoutReason());
        assertEquals(text, message.getLogoutReasonText());
        assertEquals(lastReceivedSeq, message.getLastReceivedSequenceNumber());
        assertEquals(numUnits, message.getNumberOfUnits());
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // Text is NUL-padded per BOE spec v2.11.90 (Text field type)
        // "User requested logout" = 21 bytes; remaining 39 bytes are 0x00
        LogoutResponseMessage message = new LogoutResponseMessage(LogoutResponseMessage.REASON_USER_REQUESTED, "User requested logout", 10, 1);
        message.setMatchingUnit((byte) 2);
        message.setSequenceNumber(54321);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA,           // StartOfMessage
                0x4A, 0x00,                          // MessageLength = 74
                0x08,                                // MessageType
                0x02,                                // MatchingUnit
                (byte) 0x31, (byte) 0xD4, 0x00, 0x00, // SequenceNumber = 54321
                'U',                                 // LogoutReason
                'U', 's', 'e', 'r', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', 'e', 'd', ' ', 'l', 'o', 'g', 'o', 'u', 't', // text (21 bytes)
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // NUL padding (39 bytes)
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0,
                0x0A, 0x00, 0x00, 0x00,              // LastReceivedSequenceNumber = 10
                0x01                                 // NumberOfUnits = 1
        };

        assertArrayEquals(expected, message.toBytes());
    }

    @Test
    void constructor_shouldParseByteArrayCorrectly() {
        // Parser uses trim() so NUL-padded bytes are correctly stripped
        byte[] data = new byte[76];
        data[0] = (byte) 0xBA; data[1] = (byte) 0xBA;
        data[2] = 0x4A; data[3] = 0x00;
        data[4] = 0x08;
        data[5] = 0x02;
        // SequenceNumber = 54321 = 0xD431 little-endian
        data[6] = (byte) 0x31; data[7] = (byte) 0xD4; data[8] = 0x00; data[9] = 0x00;
        data[10] = 'U';
        // "User requested logout" = 21 bytes starting at offset 11
        String text = "User requested logout";
        for (int i = 0; i < text.length(); i++) data[11 + i] = (byte) text.charAt(i);
        // bytes 32-70 remain 0x00 (NUL padding)
        // LastReceivedSequenceNumber = 10 at offset 71
        data[71] = 0x0A; data[72] = 0x00; data[73] = 0x00; data[74] = 0x00;
        data[75] = 0x01;

        LogoutResponseMessage message = new LogoutResponseMessage(data);

        assertEquals(LogoutResponseMessage.REASON_USER_REQUESTED, message.getLogoutReason());
        assertEquals("User requested logout", message.getLogoutReasonText());
        assertEquals(10, message.getLastReceivedSequenceNumber());
        assertEquals(1, message.getNumberOfUnits());
        assertEquals(2, message.getMatchingUnit());
        assertEquals(54321, message.getSequenceNumber());
    }

    @Test
    void reasonProtocolViolation_constantExists() {
        assertEquals((byte) '!', LogoutResponseMessage.REASON_PROTOCOL_VIOLATION);
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(null));
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidStartOfMessage() {
        byte[] data = new byte[76];
        data[0] = (byte) 0xFF; data[1] = (byte) 0xFF;
        data[2] = 0x4A; data[3] = 0x00;
        data[4] = 0x08;
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidMessageType() {
        byte[] data = new byte[76];
        data[0] = (byte) 0xBA; data[1] = (byte) 0xBA;
        data[2] = 0x4A; data[3] = 0x00;
        data[4] = 0x09; // wrong type (0x09 = ServerHeartbeat)
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(data));
    }

    @Test
    void setters_shouldSetCorrectValues() {
        LogoutResponseMessage message = new LogoutResponseMessage((byte) 0, "", 0, 0);

        message.setMatchingUnit((byte) 2);
        message.setSequenceNumber(54321);

        assertEquals(2, message.getMatchingUnit());
        assertEquals(54321, message.getSequenceNumber());
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        LogoutResponseMessage message = new LogoutResponseMessage(LogoutResponseMessage.REASON_USER_REQUESTED, "User requested logout", 10, 1);
        String expected = "LogoutResponseMessage{reason=U, text='User requested logout', lastReceivedSeq=10, numberOfUnits=1, matchingUnit=0, sequenceNumber=0}";

        assertEquals(expected, message.toString());
    }
}
