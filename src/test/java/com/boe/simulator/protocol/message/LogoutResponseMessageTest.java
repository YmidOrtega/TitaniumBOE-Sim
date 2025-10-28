package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LogoutResponseMessageTest {

    @Test
    void constructor_shouldSetProperties_whenGivenValidArguments() {
        // Arrange
        byte reason = LogoutResponseMessage.REASON_USER_REQUESTED;
        String text = "User requested logout";
        int lastReceivedSeq = 10;
        int numUnits = 1;
        byte matchingUnit = 2;
        int sequenceNumber = 54321;

        // Act
        LogoutResponseMessage message = new LogoutResponseMessage(reason, text, lastReceivedSeq, numUnits);
        message.setMatchingUnit(matchingUnit);
        message.setSequenceNumber(sequenceNumber);

        // Assert
        assertEquals(reason, message.getLogoutReason());
        assertEquals(text, message.getLogoutReasonText());
        assertEquals(lastReceivedSeq, message.getLastReceivedSequenceNumber());
        assertEquals(numUnits, message.getNumberOfUnits());
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // Arrange
        LogoutResponseMessage message = new LogoutResponseMessage(LogoutResponseMessage.REASON_USER_REQUESTED, "User requested logout", 10, 1);
        message.setMatchingUnit((byte) 2);
        message.setSequenceNumber(54321);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA, // Start of Message
                0x4A, 0x00, // Message Length
                0x08, // Message Type
                0x02, // Matching Unit
                (byte) 0x31, (byte) 0xD4, 0x00, 0x00, // Sequence Number
                'U', // Logout Reason
                'U', 's', 'e', 'r', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', 'e', 'd', ' ', 'l', 'o', 'g', 'o', 'u', 't', // Logout Reason Text
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x0A, 0x00, 0x00, 0x00, // Last Received Sequence Number
                0x01 // Number Of Units
        };

        // Act
        byte[] actual = message.toBytes();

        // Assert
        assertArrayEquals(expected, actual);
    }

    @Test
    void constructor_shouldParseByteArrayCorrectly() {
        // Arrange
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA, // Start of Message
                0x4A, 0x00, // Message Length
                0x08, // Message Type
                0x02, // Matching Unit
                (byte) 0x31, (byte) 0xD4, 0x00, 0x00, // Sequence Number
                'U', // Logout Reason
                'U', 's', 'e', 'r', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', 'e', 'd', ' ', 'l', 'o', 'g', 'o', 'u', 't', // Logout Reason Text
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x0A, 0x00, 0x00, 0x00, // Last Received Sequence Number
                0x01 // Number Of Units
        };

        // Act
        LogoutResponseMessage message = new LogoutResponseMessage(data);

        // Assert
        assertEquals(LogoutResponseMessage.REASON_USER_REQUESTED, message.getLogoutReason());
        assertEquals("User requested logout", message.getLogoutReasonText());
        assertEquals(10, message.getLastReceivedSequenceNumber());
        assertEquals(1, message.getNumberOfUnits());
        assertEquals(2, message.getMatchingUnit());
        assertEquals(54321, message.getSequenceNumber());
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(null));
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsTooShort() {
        // Arrange
        byte[] shortArray = {0x01, 0x02, 0x03};

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(shortArray));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidStartOfMessage() {
        // Arrange
        byte[] data = {
                (byte) 0xFF, (byte) 0xFF, // Invalid Start of Message
                0x4A, 0x00, 0x08, 0x02, (byte) 0x31, (byte) 0xD4, 0x00, 0x00, 'U', 'U', 's', 'e', 'r', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', 'e', 'd', ' ', 'l', 'o', 'g', 'o', 'u', 't',
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x0A, 0x00, 0x00, 0x00, 0x01
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidMessageType() {
        // Arrange
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA, // Start of Message
                0x4A, 0x00, // Message Length
                0x09, // Invalid Message Type
                0x02, (byte) 0x31, (byte) 0xD4, 0x00, 0x00, 'U', 'U', 's', 'e', 'r', ' ', 'r', 'e', 'q', 'u', 'e', 's', 't', 'e', 'd', ' ', 'l', 'o', 'g', 'o', 'u', 't',
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x0A, 0x00, 0x00, 0x00, 0x01
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LogoutResponseMessage(data));
    }

    @Test
    void setters_shouldSetCorrectValues() {
        // Arrange
        LogoutResponseMessage message = new LogoutResponseMessage((byte) 0, "", 0, 0);
        byte matchingUnit = 2;
        int sequenceNumber = 54321;

        // Act
        message.setMatchingUnit(matchingUnit);
        message.setSequenceNumber(sequenceNumber);

        // Assert
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        // Arrange
        LogoutResponseMessage message = new LogoutResponseMessage(LogoutResponseMessage.REASON_USER_REQUESTED, "User requested logout", 10, 1);
        String expected = "LogoutResponseMessage{reason=U, text='User requested logout', lastReceivedSeq=10, numberOfUnits=1, matchingUnit=0, sequenceNumber=0}";

        // Act
        String actual = message.toString();

        // Assert
        assertEquals(expected, actual);
    }
}