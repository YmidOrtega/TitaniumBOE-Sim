package com.boe.simulator.protocol.message;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BoeMessageTest {

    @Test
    void constructor_shouldCreateBoeMessageWithValidData() {
        byte[] data = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(data);
        assertNotNull(message);
        assertEquals(4, message.getLength());
        assertArrayEquals(data, message.getData());
    }

    @Test
    void constructor_shouldThrowExceptionForNullData() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(null),
                "Expected constructor to throw IllegalArgumentException for null data, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 4 bytes"));
    }

    @Test
    void constructor_shouldThrowExceptionForDataLessThanFourBytes() {
        byte[] data = {0x01, 0x02, 0x03};
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> new BoeMessage(data),
                "Expected constructor to throw IllegalArgumentException for data less than 4 bytes, but it didn't");
        assertTrue(thrown.getMessage().contains("Message data must be at least 4 bytes"));
    }

    @Test
    void getData_shouldReturnCopyOfInternalData() {
        byte[] originalData = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(originalData);
        byte[] retrievedData = message.getData();

        assertArrayEquals(originalData, retrievedData);
        assertNotSame(originalData, retrievedData);

        originalData[0] = 0x05;
        assertNotEquals(originalData[0], retrievedData[0]);
    }

    @Test
    void getLength_shouldReturnCorrectTotalLength() {
        byte[] data = {0x04, 0x00, 0x01, 0x02};
        BoeMessage message = new BoeMessage(data);
        assertEquals(4, message.getLength());
    }

    @Test
    void getPayload_shouldReturnCorrectPayload() {
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x02
        };
        BoeMessage message = new BoeMessage(data);
        byte[] expectedPayload = {0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01, 0x02};
        assertArrayEquals(expectedPayload, message.getPayload());
    }

    @Test
    void getPayload_shouldReturnEmptyArrayForMinimalMessage() {
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x02, 0x00
        };
        BoeMessage message = new BoeMessage(data);
        byte[] expectedPayload = {};
        assertArrayEquals(expectedPayload, message.getPayload());
    }

    @Test
    void getPayload_shouldReturnCopyOfPayload() {

        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x0A, 0x00,
                0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x02
        };

        BoeMessage message = new BoeMessage(data);

        byte[] initialPayload = message.getPayload();

        initialPayload[0] = 0x05;

        byte[] freshPayload = message.getPayload();

        assertNotEquals(initialPayload[0], freshPayload[0]);
        assertEquals(0x01, freshPayload[0]);
    }

    @Test
    void getLengthField_shouldReturnCorrectLittleEndianShort() {
        byte[] data1 = {(byte) 0xBA, (byte) 0xBA, 0x04, 0x00};
        BoeMessage message1 = new BoeMessage(data1);
        assertEquals(4, message1.getLengthField());

        byte[] data2 = {(byte) 0xBA, (byte) 0xBA, 0x00, 0x01};
        BoeMessage message2 = new BoeMessage(data2);
        assertEquals(256, message2.getLengthField());

        byte[] data3 = {(byte) 0xBA, (byte) 0xBA, (byte) 0xFF, (byte) 0x7F};
        BoeMessage message3 = new BoeMessage(data3);
        assertEquals(32767, message3.getLengthField());

        byte[] data4 = {(byte) 0xBA, (byte) 0xBA, (byte) 0xFF, (byte) 0xFF};
        BoeMessage message4 = new BoeMessage(data4);
        assertEquals(-1, message4.getLengthField());
    }

    @Test
    void toString_shouldReturnFormattedString() {
        byte[] data = {
                (byte) 0xBA, (byte) 0xBA,
                0x04, 0x00
        };
        BoeMessage message = new BoeMessage(data);
        String expectedString = "BoeMessage{length=4, lengthField=4, messageType=0x00, validMarker=true}";
        assertEquals(expectedString, message.toString());
    }
}
