package com.boe.simulator.protocol.serialization;

import com.boe.simulator.protocol.message.BoeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class BoeMessageSerializerTest {

    private final BoeMessageSerializer serializer = new BoeMessageSerializer();
    
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static byte[] buildFullPayload(byte[] variableData) {
        byte[] full = new byte[2 + variableData.length];
        full[0] = 0x01;
        full[1] = 0x00;
        System.arraycopy(variableData, 0, full, 2, variableData.length);
        return full;
    }

    @Test
    void serialize_payload_shouldPrependHeaderAndReturnCorrectBytes() {
        byte[] payload = "Hello World".getBytes();
        byte[] serializedMessage = serializer.serialize(payload);

        int expectedMessageLength = 2 + payload.length;
        int expectedTotalLength = 2 + expectedMessageLength;

        assertEquals(expectedTotalLength, serializedMessage.length);

        assertEquals(START_OF_MESSAGE_1, serializedMessage[0]);
        assertEquals(START_OF_MESSAGE_2, serializedMessage[1]);

        ByteBuffer buffer = ByteBuffer.wrap(serializedMessage, 2, 2).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(expectedMessageLength, buffer.getShort() & 0xFFFF);

        byte[] actualPayload = new byte[payload.length];
        System.arraycopy(serializedMessage, 4, actualPayload, 0, payload.length);
        assertArrayEquals(payload, actualPayload);
    }

    @Test
    void serialize_payload_shouldThrowExceptionForNullPayload() {
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> serializer.serialize((byte[]) null),
                "Expected serialize to throw IllegalArgumentException for null payload");
        assertTrue(thrown.getMessage().contains("cannot be null"));
    }

    @Test
    void serialize_payload_shouldThrowExceptionForEmptyPayload() {
        byte[] payload = new byte[0];
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> serializer.serialize(payload),
                "Expected serialize to throw IllegalArgumentException for empty payload");
        assertTrue(thrown.getMessage().contains("cannot be null or empty"));
    }

    @Test
    void serialize_payload_shouldThrowExceptionIfMessageTooLarge() {

        byte[] largePayload = new byte[65534];
        
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class,
                () -> serializer.serialize(largePayload),
                "Expected serialize to throw IllegalArgumentException for large payload");
        assertTrue(thrown.getMessage().contains("Message too large"));
    }

    @Test
    void serialize_boeMessage_shouldReturnBoeMessageData() {
        BoeMessage mockMessage = Mockito.mock(BoeMessage.class);
        byte[] expectedData = new byte[]{START_OF_MESSAGE_1, START_OF_MESSAGE_2, 0x05, 0x00, 0x01};
        when(mockMessage.getData()).thenReturn(expectedData);

        byte[] actualData = serializer.serialize(mockMessage);
        assertArrayEquals(expectedData, actualData);
    }

    @Test
    void deserialize_shouldReconstructBoeMessageCorrectly() throws IOException {
        byte[] variableData = "Test Message".getBytes();

        final int FIXED_FIELDS_SIZE = 6; // Type (1) + Unit (1) + SeqNum (4)
        byte[] fullPayload = new byte[FIXED_FIELDS_SIZE + variableData.length];

        fullPayload[0] = 0x01; // MessageType
        fullPayload[1] = 0x00; // MatchingUnit
        // SeqNum (4 bytes) se inicializa a 0x00 automáticamente (Offset 2 a 5)

        System.arraycopy(variableData, 0, fullPayload, FIXED_FIELDS_SIZE, variableData.length);

        byte[] serialized = serializer.serialize(fullPayload);

        InputStream inputStream = new ByteArrayInputStream(serialized);
        BoeMessage deserializedMessage = serializer.deserialize(inputStream);

        assertArrayEquals(serialized, deserializedMessage.getData());

        assertArrayEquals(variableData, deserializedMessage.getPayload());
     assertArrayEquals(variableData, deserializedMessage.getPayload());
    }

    @Test
    void deserialize_shouldThrowIOExceptionForEmptyStream() {
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);
        
        IOException thrown = assertThrows(IOException.class,
                () -> serializer.deserialize(emptyStream),
                "Expected deserialize to throw IOException for empty stream");
        assertTrue(thrown.getMessage().contains("End of stream reached"));
    }

    @Test
    void deserialize_shouldThrowIOExceptionForInvalidStartMarker() {
        // Invalid start marker
        byte[] invalidMessage = new byte[]{0x00, 0x00, 0x05, 0x00, 0x01};
        InputStream invalidStream = new ByteArrayInputStream(invalidMessage);
        
        IOException thrown = assertThrows(IOException.class,
                () -> serializer.deserialize(invalidStream),
                "Expected deserialize to throw IOException for invalid start marker");
        assertTrue(thrown.getMessage().contains("Invalid start of message marker"));
    }

    @Test
    void deserialize_shouldThrowIOExceptionForPrematureEndOfStream() {
        // Valid header but missing payload
        byte[] partialMessage = new byte[]{
            START_OF_MESSAGE_1, START_OF_MESSAGE_2, 
            0x0A, 0x00  // MessageLength = 10, but no payload follows
        };
        InputStream partialStream = new ByteArrayInputStream(partialMessage);
        
        IOException thrown = assertThrows(IOException.class,
                () -> serializer.deserialize(partialStream),
                "Expected deserialize to throw IOException for premature end of stream");
        assertTrue(thrown.getMessage().contains("End of stream reached"));
    }

    @Test
    void deserialize_shouldThrowIOExceptionForInvalidMessageLength() {
        byte[] invalidLengthMessage = new byte[]{
                START_OF_MESSAGE_1, START_OF_MESSAGE_2,
                0x03, 0x00
        };
        InputStream invalidStream = new ByteArrayInputStream(invalidLengthMessage);

        IOException thrown = assertThrows(IOException.class,
                () -> serializer.deserialize(invalidStream),
                "Expected deserialize to throw IOException for invalid message length");
        assertTrue(thrown.getMessage().contains("Invalid message length"));
    }

    @Test
    void deserialize_shouldHandleMinimumValidMessage() throws IOException {
        byte[] minMessage = new byte[]{
                START_OF_MESSAGE_1, START_OF_MESSAGE_2,
                0x08, 0x00,
                (byte) 0x01,
                (byte) 0x00,
                0x00, 0x00, 0x00, 0x00
        };
        InputStream inputStream = new ByteArrayInputStream(minMessage);
        BoeMessage message = serializer.deserialize(inputStream);

        assertNotNull(message);
        assertEquals(10, message.getMessageLength());
        assertEquals(0, message.getPayload().length);
    }

    @Test
    void roundTrip_serializeDeserialize_shouldPreservePayload() throws IOException {
        byte[] variablePayload = "Round trip test data with special chars: åéñ".getBytes();

        final int FIXED_FIELDS_SIZE = 6;
        byte[] fullPayload = new byte[FIXED_FIELDS_SIZE + variablePayload.length];

        fullPayload[0] = 0x01; // MessageType
        fullPayload[1] = 0x00; // MatchingUnit

        System.arraycopy(variablePayload, 0, fullPayload, FIXED_FIELDS_SIZE, variablePayload.length);

        byte[] serialized = serializer.serialize(fullPayload);

        InputStream inputStream = new ByteArrayInputStream(serialized);
        BoeMessage deserializedMessage = serializer.deserialize(inputStream);

        byte[] actualPayload = deserializedMessage.getPayload();
        assertArrayEquals(variablePayload, actualPayload);
    }

    @Test
    void roundTrip_multipleMessages_shouldDeserializeCorrectly() throws IOException {

        byte[] variablePayload1 = "First message".getBytes();
        byte[] variablePayload2 = "Second message".getBytes();

        final int FIXED_FIELDS_SIZE = 6;

        byte[] fullPayload1 = new byte[FIXED_FIELDS_SIZE + variablePayload1.length]; // 6 + 13 = 19 bytes
        fullPayload1[0] = 0x01;
        fullPayload1[1] = 0x00;

        System.arraycopy(variablePayload1, 0, fullPayload1, FIXED_FIELDS_SIZE, variablePayload1.length);
        byte[] serialized1 = serializer.serialize(fullPayload1);

        byte[] fullPayload2 = new byte[FIXED_FIELDS_SIZE + variablePayload2.length]; // 6 + 14 = 20 bytes
        fullPayload2[0] = 0x01;
        fullPayload2[1] = 0x00;

        System.arraycopy(variablePayload2, 0, fullPayload2, FIXED_FIELDS_SIZE, variablePayload2.length);
        byte[] serialized2 = serializer.serialize(fullPayload2);

        byte[] combined = new byte[serialized1.length + serialized2.length];
        System.arraycopy(serialized1, 0, combined, 0, serialized1.length);
        System.arraycopy(serialized2, 0, combined, serialized1.length, serialized2.length);

        InputStream inputStream = new ByteArrayInputStream(combined);

        BoeMessage message1 = serializer.deserialize(inputStream);
        assertArrayEquals(variablePayload1, message1.getPayload());

        BoeMessage message2 = serializer.deserialize(inputStream);
        assertArrayEquals(variablePayload2, message2.getPayload());
    }

    @Test
    void serialize_shouldHandleLargePayload() {

        int maxPayloadSize = 65533;
        byte[] largePayload = new byte[maxPayloadSize];
        for (int i = 0; i < largePayload.length; i++) {
            largePayload[i] = (byte) (i % 256);
        }
        
        byte[] serialized = serializer.serialize(largePayload);
        
        // Verify structure
        assertEquals(START_OF_MESSAGE_1, serialized[0]);
        assertEquals(START_OF_MESSAGE_2, serialized[1]);
        
        ByteBuffer buffer = ByteBuffer.wrap(serialized, 2, 2).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = buffer.getShort() & 0xFFFF;
        assertEquals(2 + maxPayloadSize, messageLength);
    }
}