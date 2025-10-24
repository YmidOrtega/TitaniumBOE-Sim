package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class BoeMessageTest {

    @Test
    void constructor_shouldCreateMessage_whenDataIsValid() {
        // Arrange
        byte[] data = {0x04, 0x00, 0x01, 0x02};

        // Act
        BoeMessage message = new BoeMessage(data);

        // Assert
        assertNotNull(message);
        assertEquals(4, message.getLength());
        assertArrayEquals(data, message.getData());
    }

    @Test
    void constructor_shouldThrowException_whenDataIsNull() {
        // Arrange, Act & Assert
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(null),
                "Expected constructor to throw for null data, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 4 bytes"));
    }

    @Test
    void constructor_shouldThrowException_whenDataIsTooShort() {
        // Arrange
        byte[] data = {0x01, 0x02, 0x03};

        // Arrange, Act & Assert
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(data),
                "Expected constructor to throw for data less than 4 bytes, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 4 bytes"));
    }

    @Test
    void getData_shouldReturnDefensiveCopy() {
        // Arrange
        byte[] originalData = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(originalData);

        // Act
        byte[] retrievedData = message.getData();
        originalData[0] = 0x05;

        // Assert
        assertNotEquals(originalData[0], retrievedData[0]);
    }

    @Test
    void getLength_shouldReturnCorrectLength() {
        // Arrange
        byte[] data = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(data);

        // Act
        int length = message.getLength();

        // Assert
        assertEquals(4, length);
    }

    @Test
    void getPayload_shouldReturnCorrectPayloadBytes() {
        // Arrange
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x02
        };
        BoeMessage message = new BoeMessage(data);
        byte[] expectedPayload = {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02};

        // Act
        byte[] actualPayload = message.getPayload();

        // Assert
        assertArrayEquals(expectedPayload, actualPayload);
    }

    @Test
    void getPayload_shouldReturnEmptyArray_whenMessageHasNoPayload() {
        // Arrange
        byte[] data = {(byte) 0xBA, (byte) 0xBA, 0x02, 0x00};
        BoeMessage message = new BoeMessage(data);

        // Act
        byte[] payload = message.getPayload();

        // Assert
        assertEquals(0, payload.length);
    }

    @Test
    void getPayload_shouldReturnDefensiveCopy() {
        // Arrange
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x02
        };
        BoeMessage message = new BoeMessage(data);
        byte[] initialPayload = message.getPayload();

        // Act
        initialPayload[0] = 0x05;

        // Assert
        byte[] freshPayload = message.getPayload();
        assertNotEquals(initialPayload[0], freshPayload[0]);
        assertEquals(0x01, freshPayload[0]);
    }

    @ParameterizedTest
    @MethodSource("lengthFieldTestCases")
    void getLengthField_shouldReturnCorrectValue_forLittleEndianBytes(int expectedLength, byte[] data) {
        // Arrange
        BoeMessage message = new BoeMessage(data);

        // Act
        int actualLength = message.getLengthField();

        // Assert
        assertEquals(expectedLength, actualLength);
    }

    private static Stream<Arguments> lengthFieldTestCases() {
        return Stream.of(
                Arguments.of(4, new byte[]{(byte) 0xBA, (byte) 0xBA, 0x04, 0x00}),
                Arguments.of(256, new byte[]{(byte) 0xBA, (byte) 0xBA, 0x00, 0x01}),
                Arguments.of(32767, new byte[]{(byte) 0xBA, (byte) 0xBA, (byte) 0xFF, (byte) 0x7F}),
                Arguments.of(-1, new byte[]{(byte) 0xBA, (byte) 0xBA, (byte) 0xFF, (byte) 0xFF})
        );
    }

    @Test
    void toString_shouldReturnCorrectStringRepresentation() {
        // Arrange
        byte[] data = {(byte) 0xBA, (byte) 0xBA, 0x04, 0x00};
        BoeMessage message = new BoeMessage(data);
        String expectedString = "BoeMessage{length=4, lengthField=4, messageType=0x00, validMarker=true}";

        // Act
        String actualString = message.toString();

        // Assert
        assertEquals(expectedString, actualString);
    }
}