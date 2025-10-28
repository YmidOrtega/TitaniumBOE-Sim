package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoginResponseMessageTest {

    @Test
    void constructor_shouldSetProperties_whenGivenValidArguments() {
        // Arrange
        byte status = LoginResponseMessage.STATUS_ACCEPTED;
        String text = "Login OK";
        int lastReceivedSeq = 1;
        int numUnits = 5;
        byte matchingUnit = 1;
        int sequenceNumber = 12345;

        // Act
        LoginResponseMessage message = new LoginResponseMessage(status, text, lastReceivedSeq, numUnits);
        message.setMatchingUnit(matchingUnit);
        message.setSequenceNumber(sequenceNumber);

        // Assert
        assertEquals(status, message.getLoginResponseStatus());
        assertEquals(text, message.getLoginResponseText());
        assertEquals(lastReceivedSeq, message.getLastReceivedSequenceNumber());
        assertEquals(numUnits, message.getNumberOfUnits());
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void isAccepted_shouldReturnTrue_whenStatusIsAccepted() {
        // Arrange
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "text", 0, 0);

        // Act & Assert
        assertTrue(message.isAccepted());
        assertFalse(message.isRejected());
    }

    @Test
    void isRejected_shouldReturnTrue_whenStatusIsRejected() {
        // Arrange
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_REJECTED, "text", 0, 0);

        // Act & Assert
        assertFalse(message.isAccepted());
        assertTrue(message.isRejected());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // Arrange
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 1, 5);
        message.setMatchingUnit((byte) 1);
        message.setSequenceNumber(12345);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA, // Start of Message
                0x4A, 0x00, // Message Length
                0x07, // Message Type
                0x01, // Matching Unit
                0x39, 0x30, 0x00, 0x00, // Sequence Number
                'A', // Login Response Status
                'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K', // Login Response Text
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x01, 0x00, 0x00, 0x00, // Last Received Sequence Number
                0x05 // Number Of Units
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
                0x07, // Message Type
                0x01, // Matching Unit
                0x39, 0x30, 0x00, 0x00, // Sequence Number
                'A', // Login Response Status
                'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K', // Login Response Text
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x01, 0x00, 0x00, 0x00, // Last Received Sequence Number
                0x05 // Number Of Units
        };

        // Act
        LoginResponseMessage message = new LoginResponseMessage(data);

        // Assert
        assertEquals(LoginResponseMessage.STATUS_ACCEPTED, message.getLoginResponseStatus());
        assertEquals("Login OK", message.getLoginResponseText());
        assertEquals(1, message.getLastReceivedSequenceNumber());
        assertEquals(5, message.getNumberOfUnits());
        assertEquals(1, message.getMatchingUnit());
        assertEquals(12345, message.getSequenceNumber());
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(null));
    }

    @Test
    void constructor_shouldThrowException_whenByteArrayIsTooShort() {
        // Arrange
        byte[] shortArray = {0x01, 0x02, 0x03};

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(shortArray));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidStartOfMessage() {
        // Arrange
        byte[] data = {
                (byte) 0xFF, (byte) 0xFF, // Invalid Start of Message
                0x4A, 0x00, 0x07, 0x01, 0x39, 0x30, 0x00, 0x00, 'A', 'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K',
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x01, 0x00, 0x00, 0x00, 0x05
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void constructor_shouldThrowException_whenInvalidMessageType() {
        // Arrange
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA, // Start of Message
                0x4A, 0x00, // Message Length
                0x08, // Invalid Message Type
                0x01, 0x39, 0x30, 0x00, 0x00, 'A', 'L', 'o', 'g', 'i', 'n', ' ', 'O', 'K',
                ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ', ' ',
                0x01, 0x00, 0x00, 0x00, 0x05
        };

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginResponseMessage(data));
    }

    @Test
    void setters_shouldSetCorrectValues() {
        // Arrange
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "text", 0, 0);
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
        LoginResponseMessage message = new LoginResponseMessage(LoginResponseMessage.STATUS_ACCEPTED, "Login OK", 1, 5);
        String expected = "LoginResponseMessage{status=A, text='Login OK', lastReceivedSeq=1, numberOfUnits=5, matchingUnit=0, sequenceNumber=0}";

        // Act
        String actual = message.toString();

        // Assert
        assertEquals(expected, actual);
    }
}