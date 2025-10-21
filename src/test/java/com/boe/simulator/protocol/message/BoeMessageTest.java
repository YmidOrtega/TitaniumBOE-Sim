package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoeMessageTest {

    @Test
    void constructor_shouldCreateBoeMessageWithValidData() {
        byte[] data = {0x04, 0x00, 0x01, 0x02}; // Length 4, Payload 01 02
        BoeMessage message = new BoeMessage(data);
        assertNotNull(message);
        assertEquals(4, message.getMessageLength());
        assertArrayEquals(data, message.getData());
    }

    @Test
    void constructor_shouldThrowExceptionForNullData() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(null),
                "Expected constructor to throw IllegalArgumentException for null data, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 2 bytes"));
    }

    @Test
    void constructor_shouldThrowExceptionForDataLessThanTwoBytes() {
        byte[] data = {0x01}; // Only 1 byte
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(data),
                "Expected constructor to throw IllegalArgumentException for data less than 2 bytes, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 2 bytes"));
    }

    @Test
    void getData_shouldReturnCopyOfInternalData() {
        byte[] originalData = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(originalData);
        byte[] retrievedData = message.getData();

        assertArrayEquals(originalData, retrievedData);
        assertNotSame(originalData, retrievedData); // Ensure it's a copy

        // Modify originalData to ensure immutability
        originalData[0] = 0x05;
        assertNotEquals(originalData[0], retrievedData[0]);
    }

    @Test
    void getLength_shouldReturnCorrectTotalLength() {
        byte[] data = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(data);
        assertEquals(4, message.getMessageLength());
    }

    @Test
    void getPayload_shouldReturnCorrectPayload() {
        byte[] data = {
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x02
        };

        BoeMessage message = new BoeMessage(data);
        byte[] expectedPayload = {0x01, 0x02};
        assertArrayEquals(expectedPayload, message.getPayload()); // PASA
    }

    @Test
    void getPayload_shouldReturnEmptyArrayForMessageWithOnlyLengthField() {
        byte[] data = {0x02, 0x00}; // Only length field, no payload
        BoeMessage message = new BoeMessage(data);
        byte[] expectedPayload = {};
        assertArrayEquals(expectedPayload, message.getPayload());
    }

    @Test
    void getPayload_shouldReturnCopyOfPayload() {
        // Length (10=0x0A, 0x00), Type (0x01), Unit (0x00), SeqNum (4 bytes de 0s), Payload (0x01, 0x02)
        // Longitud total del array: 10 bytes
        byte[] data = {
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00, // Fixed Body
                0x01, 0x02                         // Payload
        };

        BoeMessage message = new BoeMessage(data);

        byte[] initialPayload = message.getPayload(); // Retorna {0x01, 0x02}

        // Modificar la copia del payload
        initialPayload[0] = 0x05;

        byte[] freshPayload = message.getPayload(); // Segunda copia (debe ser {0x01, 0x02})

        // Assert that the modified initialPayload is different from the freshPayload (Check Copy)
        assertNotEquals(initialPayload[0], freshPayload[0]); // PASA (0x05 != 0x01)

        // The original value should still be there (Check Immutability of source)
        assertEquals(0x01, freshPayload[0]); // PASA
    }

    @Test
    void getLengthField_shouldReturnCorrectLittleEndianShort() {
        // Example: length 4 (0x0004)
        byte[] data1 = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message1 = new BoeMessage(data1);
        assertEquals(4, message1.getLengthField());

        // Example: length 256 (0x0100)
        byte[] data2 = {0x00, 0x01, 0x03, 0x04};
        BoeMessage message2 = new BoeMessage(data2);
        assertEquals(256, message2.getLengthField());

        // Example: max short value 32767 (0x7FFF)
        byte[] data3 = {(byte) 0xFF, (byte) 0x7F, 0x05, 0x06};
        BoeMessage message3 = new BoeMessage(data3);
        assertEquals(32767, message3.getLengthField());

        // Example: negative short value (interpreted as unsigned short 0xFFFF = 65535)
        // The getLengthField() method returns a short, so it will be -1 for 0xFFFF
        // If it's meant to be an unsigned short, the method signature should be int.
        // Given the context of message length, it's likely an unsigned short.
        // Let's assume it's an unsigned short for testing, so we'll cast to int.
        // The current implementation returns a short, so it will be negative for values > 32767.
        // The BoeMessageSerializer uses `int messageLength = lengthBuffer.getShort() & 0xFFFF;` for unsigned interpretation.
        // So, the test should reflect the signed short value.
        byte[] data4 = {(byte) 0xFF, (byte) 0xFF, 0x07, 0x08};
        BoeMessage message4 = new BoeMessage(data4);
        assertEquals(-1, message4.getLengthField()); // 0xFFFF as signed short is -1
    }

    @Test
    void toString_shouldReturnFormattedString() {
        byte[] data = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(data);
        String expectedString = "BoeMessage{length=4, lengthField=4}";
        assertEquals(expectedString, message.toString());
    }
}
