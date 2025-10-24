package com.boe.simulator.protocol.serialization;

import com.boe.simulator.protocol.message.BoeMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

class BoeMessageSerializerTest {

    private BoeMessageSerializer serializer;

    @Mock
    private InputStream mockInputStream;
    
    private AutoCloseable closeable;

    @BeforeEach
    void setUp() {
        closeable = MockitoAnnotations.openMocks(this);
        serializer = new BoeMessageSerializer();
    }
    
    @AfterEach
    void tearDown() throws Exception {
        closeable.close();
    }

    @Test
    void serialize_payload_shouldPrependHeaderAndReturnCorrectBytes() {
        // Arrange
        byte[] payload = "Hello World".getBytes();
        int expectedMessageLength = 2 + payload.length;
        int expectedTotalLength = 2 + expectedMessageLength;

        // Act
        byte[] serializedMessage = serializer.serialize(payload);

        // Assert
        assertEquals(expectedTotalLength, serializedMessage.length);
        assertEquals((byte) 0xBA, serializedMessage[0]);
        assertEquals((byte) 0xBA, serializedMessage[1]);

        ByteBuffer buffer = ByteBuffer.wrap(serializedMessage, 2, 2).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(expectedMessageLength, buffer.getShort() & 0xFFFF);

        byte[] actualPayload = new byte[payload.length];
        System.arraycopy(serializedMessage, 4, actualPayload, 0, payload.length);
        assertArrayEquals(payload, actualPayload);
    }

    @Test
    void serialize_payload_shouldThrowException_whenPayloadIsNull() {
        // Arrange, Act & Assert
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize((byte[]) null));
    }

    @Test
    void serialize_payload_shouldThrowException_whenPayloadIsEmpty() {
        // Arrange
        byte[] payload = new byte[0];

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(payload));
    }

    @Test
    void serialize_payload_shouldThrowException_whenMessageIsTooLarge() {
        // Arrange
        byte[] largePayload = new byte[65534];

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> serializer.serialize(largePayload));
    }

    @Test
    void serialize_boeMessage_shouldReturnDataFromMessage() {
        // Arrange
        BoeMessage mockMessage = org.mockito.Mockito.mock(BoeMessage.class);
        byte[] expectedData = {(byte) 0xBA, (byte) 0xBA, 0x05, 0x00, 0x01};
        when(mockMessage.getData()).thenReturn(expectedData);

        // Act
        byte[] actualData = serializer.serialize(mockMessage);

        // Assert
        assertArrayEquals(expectedData, actualData);
    }

    @Test
    void deserialize_shouldReconstructMessageCorrectly_whenStreamIsValid() throws IOException {
        // Arrange
        byte[] payload = "Test Message".getBytes();
        byte[] serialized = serializer.serialize(payload);
        InputStream inputStream = new ByteArrayInputStream(serialized);

        // Act
        BoeMessage deserializedMessage = serializer.deserialize(inputStream);

        // Assert
        assertArrayEquals(serialized, deserializedMessage.getData());
        assertArrayEquals(payload, deserializedMessage.getPayload());
    }

    @Test
    void deserialize_shouldThrowException_whenStreamIsEmpty() {
        // Arrange
        InputStream emptyStream = new ByteArrayInputStream(new byte[0]);

        // Act & Assert
        assertThrows(IOException.class, () -> serializer.deserialize(emptyStream));
    }

    @Test
    void deserialize_shouldThrowException_whenMarkerIsInvalid() {
        // Arrange
        byte[] invalidMessage = {0x00, 0x00, 0x05, 0x00, 0x01};
        InputStream invalidStream = new ByteArrayInputStream(invalidMessage);

        // Act & Assert
        assertThrows(IOException.class, () -> serializer.deserialize(invalidStream));
    }

    @Test
    void deserialize_shouldThrowException_whenStreamEndsPrematurely() {
        // Arrange
        byte[] partialMessage = {(byte) 0xBA, (byte) 0xBA, 0x0A, 0x00};
        InputStream partialStream = new ByteArrayInputStream(partialMessage);

        // Act & Assert
        assertThrows(IOException.class, () -> serializer.deserialize(partialStream));
    }

    @Test
    void deserialize_shouldThrowException_whenMessageLengthIsInvalid() {
        // Arrange
        byte[] invalidLengthMessage = {(byte) 0xBA, (byte) 0xBA, 0x03, 0x00};
        InputStream invalidStream = new ByteArrayInputStream(invalidLengthMessage);

        // Act & Assert
        assertThrows(IOException.class, () -> serializer.deserialize(invalidStream));
    }

    @Test
    void deserialize_shouldHandleMinimumValidMessage() throws IOException {
        // Arrange
        byte[] minMessage = {
                (byte) 0xBA, (byte) 0xBA, // Start of message
                0x08, 0x00,             // Message length (2 + 6)
                (byte) 0x01,            // Message Type
                (byte) 0x00,            // Matching Unit
                0x00, 0x00, 0x00, 0x00    // Sequence Number
        };
        InputStream inputStream = new ByteArrayInputStream(minMessage);

        // Act
        BoeMessage message = serializer.deserialize(inputStream);

        // Assert
        assertNotNull(message);
        assertEquals(10, message.getLength());
        assertEquals(6, message.getPayload().length);
    }

    @Test
    void roundTrip_serializeAndDeserialize_shouldPreservePayload() throws IOException {
        // Arrange
        byte[] payload = "Round trip test data with special chars: åéñ".getBytes();

        // Act
        byte[] serialized = serializer.serialize(payload);
        InputStream inputStream = new ByteArrayInputStream(serialized);
        BoeMessage deserializedMessage = serializer.deserialize(inputStream);

        // Assert
        assertArrayEquals(payload, deserializedMessage.getPayload());
    }

    @Test
    void roundTrip_multipleMessages_shouldDeserializeCorrectly() throws IOException {
        // Arrange
        byte[] payload1 = "First message".getBytes();
        byte[] payload2 = "Second message".getBytes();
        byte[] serialized1 = serializer.serialize(payload1);
        byte[] serialized2 = serializer.serialize(payload2);
        byte[] combined = new byte[serialized1.length + serialized2.length];
        System.arraycopy(serialized1, 0, combined, 0, serialized1.length);
        System.arraycopy(serialized2, 0, combined, serialized1.length, serialized2.length);
        InputStream inputStream = new ByteArrayInputStream(combined);

        // Act
        BoeMessage message1 = serializer.deserialize(inputStream);
        BoeMessage message2 = serializer.deserialize(inputStream);

        // Assert
        assertArrayEquals(payload1, message1.getPayload());
        assertArrayEquals(payload2, message2.getPayload());
    }

    @Test
    void serialize_shouldHandleMaxPayloadSize() {
        // Arrange
        int maxPayloadSize = 65533;
        byte[] largePayload = new byte[maxPayloadSize];

        // Act
        byte[] serialized = serializer.serialize(largePayload);
        ByteBuffer buffer = ByteBuffer.wrap(serialized, 2, 2).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = buffer.getShort() & 0xFFFF;

        // Assert
        assertEquals(2 + maxPayloadSize, messageLength);
    }
}