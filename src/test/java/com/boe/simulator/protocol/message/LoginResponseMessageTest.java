package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginResponseMessageTest {

    @Test
    void constructor_shouldSetProperties_whenGivenValidArguments() {
        byte status = LoginResponseMessage.STATUS_ACCEPTED;
        String text = "Login OK";
        int lastReceivedSeq = 1;
        int numUnits = 5;
        byte matchingUnit = 1;
        int sequenceNumber = 12345;

        LoginResponseMessage message = new LoginResponseMessage(status, text, lastReceivedSeq, numUnits);
        message.setMatchingUnit(matchingUnit);
        message.setSequenceNumber(sequenceNumber);

        assertEquals(status, message.getLoginResponseStatus());
        assertEquals(text, message.getLoginResponseText());
        assertEquals(lastReceivedSeq, message.getLastReceivedSequenceNumber());
        assertEquals(numUnits, message.getNumberOfUnits());
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void isAccepted_shouldReturnTrue_whenStatusIsAccepted() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "text", 0, 0);

        assertTrue(message.isAccepted());
        assertFalse(message.isRejected());
    }

    @Test
    void isRejected_shouldReturnTrue_whenStatusIsNotAccepted() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_NOT_AUTHORIZED, "text", 0, 0);

        assertFalse(message.isAccepted());
        assertTrue(message.isRejected());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // MessageLength = 75 (0x4B): type(1)+unit(1)+seq(4)+status(1)+text(60)+noReplay(1)+lastSeq(4)+numUnits(1) + 2 (length field) = 75
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 1, 5);
        message.setMatchingUnit((byte) 1);
        message.setSequenceNumber(12345);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA, // StartOfMessage
                0x4B, 0x00,               // MessageLength = 75
                0x24,                     // MessageType = 0x24 (Login Response)
                0x01,                     // MatchingUnit
                0x39, 0x30, 0x00, 0x00,   // SequenceNumber = 12345
                'A',                      // LoginResponseStatus
                'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K', // LoginResponseText (8 bytes)
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,  // NUL padding
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,                                        // 52 NUL bytes total
                '0',                      // NoUnspecifiedUnitReplay = '0'
                0x01, 0x00, 0x00, 0x00,   // LastReceivedSequenceNumber = 1
                0x05                      // NumberOfUnits = 5
        };

        byte[] actual = message.toBytes();

        assertArrayEquals(expected, actual);
    }

    @Test
    void constructor_shouldParseByteArrayCorrectly() {
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x4B, 0x00,
                0x24,
                0x01,
                0x39, 0x30, 0x00, 0x00,
                'A',
                'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K',
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
                0, 0, 0, 0,
                '0',
                0x01, 0x00, 0x00, 0x00,
                0x05
        };

        LoginResponseMessage message = new LoginResponseMessage(data);

        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, message.getLoginResponseStatus());
        assertEquals("Login OK", message.getLoginResponseText());
        assertEquals((byte) '0', message.getNoUnspecifiedUnitReplay());
        assertEquals(1, message.getLastReceivedSequenceNumber());
        assertEquals(5, message.getNumberOfUnits());
        assertEquals(1, message.getMatchingUnit());
        assertEquals(12345, message.getSequenceNumber());
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsNull() {
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(null));
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsTooShort() {
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(new byte[]{0x01, 0x02, 0x03}));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidStartOfMessage() {
        byte[] data = new byte[77];
        data[0] = (byte) 0xFF; data[1] = (byte) 0xFF;
        data[2] = 0x4B; data[3] = 0x00;
        data[4] = 0x24;
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidMessageType() {
        // 0x08 is Logout, not LoginResponse (0x24)
        byte[] data = new byte[77];
        data[0] = (byte) 0xBA; data[1] = (byte) 0xBA;
        data[2] = 0x4B; data[3] = 0x00;
        data[4] = 0x08;
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void setters_shouldSetCorrectValues() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "text", 0, 0);

        message.setMatchingUnit((byte) 2);
        message.setSequenceNumber(54321);

        assertEquals(2, message.getMatchingUnit());
        assertEquals(54321, message.getSequenceNumber());
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 1, 5);
        String expected = "LoginResponseMessage{status=A, text='Login OK', noUnspecifiedUnitReplay=0, lastReceivedSeq=1, numberOfUnits=5, matchingUnit=0, sequenceNumber=0}";

        assertEquals(expected, message.toString());
    }
}
