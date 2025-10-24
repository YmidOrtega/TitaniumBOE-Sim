package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientHeartbeatMessageTest {

    @Test
    void defaultConstructor_shouldInitializeWithDefaultValues() {
        // Arrange & Act
        ClientHeartbeatMessage message = new ClientHeartbeatMessage();

        // Assert
        assertEquals(0, message.getMatchingUnit());
        assertEquals(0, message.getSequenceNumber());
    }

    @Test
    void parameterizedConstructor_shouldSetMatchingUnitAndSequenceNumber() {
        // Arrange
        byte matchingUnit = 1;
        int sequenceNumber = 12345;

        // Act
        ClientHeartbeatMessage message = new ClientHeartbeatMessage(matchingUnit, sequenceNumber);

        // Assert
        assertEquals(matchingUnit, message.getMatchingUnit());
        assertEquals(sequenceNumber, message.getSequenceNumber());
    }

    @Test
    void toBytes_shouldReturnCorrectByteArray_whenCalled() {
        // Arrange
        ClientHeartbeatMessage message = new ClientHeartbeatMessage((byte) 1, 12345);
        byte[] expected = {
                (byte) 0xBA, (byte) 0xBA,
                0x06, 0x00,
                0x03,
                0x01,
                0x39, 0x30, 0x00, 0x00
        };

        // Act
        byte[] actual = message.toBytes();

        // Assert
        assertArrayEquals(expected, actual);
    }

    @Test
    void setters_shouldSetCorrectValues() {
        // Arrange
        ClientHeartbeatMessage message = new ClientHeartbeatMessage();
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
        ClientHeartbeatMessage message = new ClientHeartbeatMessage((byte) 1, 12345);
        String expected = "ClientHeartbeatMessage{matchingUnit=1, sequenceNumber=12345}";

        // Act
        String actual = message.toString();

        // Assert
        assertEquals(expected, actual);
    }
}