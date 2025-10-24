package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

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