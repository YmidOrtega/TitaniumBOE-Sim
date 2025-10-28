package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class LoginRequestMessageTest {

    @Test
    void constructor_shouldSetProperties_whenGivenValidArguments() {
        // Arrange
        String username = "user";
        String password = "pass";
        String sessionSubID = "S001";
        byte matchingUnit = 1;

        // Act
        LoginRequestMessage message = new LoginRequestMessage(username, password, sessionSubID, matchingUnit);

        // Assert
        assertEquals(username, message.getUsername());
        assertEquals(password, message.getPassword());
        assertEquals(sessionSubID, message.getSessionSubID());
        assertEquals(matchingUnit, message.getMatchingUnit());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"longusername"})
    void constructor_shouldThrowException_whenUsernameIsInvalid(String invalidUsername) {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginRequestMessage(invalidUsername, "pass"));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"longpasswordtoolong"})
    void constructor_shouldThrowException_whenPasswordIsInvalid(String invalidPassword) {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginRequestMessage("user", invalidPassword));
    }

    @Test
    void constructor_shouldThrowException_whenSessionSubIdIsTooLong() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> new LoginRequestMessage("user", "pass", "longs"));
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // Arrange
        LoginRequestMessage message = new LoginRequestMessage("user", "pass", "S1", (byte) 1);
        message.setSequenceNumber(12345);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA,
                0x1B, 0x00,
                0x37,
                0x01,
                0x39, 0x30, 0x00, 0x00,
                'S', '1', ' ', ' ',
                'u', 's', 'e', 'r',
                'p', 'a', 's', 's', ' ', ' ', ' ', ' ', ' ', ' ',
                0x00
        };

        // Act
        byte[] actual = message.toBytes();

        // Assert
        assertArrayEquals(expected, actual);
    }

    @Test
    void setters_shouldSetCorrectValues() {
        // Arrange
        LoginRequestMessage message = new LoginRequestMessage("user", "pass");
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
        LoginRequestMessage message = new LoginRequestMessage("user", "pass", "S1", (byte) 1);
        String expected = "LoginRequestMessage{username='user', sessionSubID='S1', matchingUnit=1, sequenceNumber=0}";

        // Act
        String actual = message.toString();

        // Assert
        assertEquals(expected, actual);
    }
}