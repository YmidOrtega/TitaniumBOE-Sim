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