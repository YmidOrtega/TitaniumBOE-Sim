package com.boe.simulator.server.validation;

import com.boe.simulator.protocol.message.BoeMessage;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MessageValidatorTest {

    @Test
    void validate_shouldReturnValid_forValidMessage() {
        // Arrange
        BoeMessage message = new BoeMessage(new byte[]{(byte) 0xBA, (byte) 0xBA, 0x02, 0x00});

        // Act
        MessageValidator.ValidationResult result = MessageValidator.validate(message);

        // Assert
        assertTrue(result.isValid());
    }

    @Test
    void validate_shouldReturnInvalid_forNullMessage() {
        // Act
        MessageValidator.ValidationResult result = MessageValidator.validate(null);

        // Assert
        assertFalse(result.isValid());
        assertEquals("Message is null", result.getMessage());
    }

    @Test
    void validate_shouldReturnInvalid_forInvalidStartMarker() {
        // Arrange
        BoeMessage message = new BoeMessage(new byte[]{0x00, 0x00, 0x04, 0x00});

        // Act
        MessageValidator.ValidationResult result = MessageValidator.validate(message);

        // Assert
        assertFalse(result.isValid());
        assertEquals("Invalid start marker", result.getMessage());
    }

    

    @Test
    void validate_shouldReturnInvalid_forMessageTooLong() {
        // Arrange
        byte[] data = new byte[70000];
        data[0] = (byte) 0xBA;
        data[1] = (byte) 0xBA;
        data[2] = (byte) 0xFF;
        data[3] = (byte) 0xFF;
        BoeMessage message = new BoeMessage(data);

        // Act
        MessageValidator.ValidationResult result = MessageValidator.validate(message);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Message too long"));
    }

    @Test
    void validate_shouldReturnInvalid_forLengthFieldMismatch() {
        // Arrange
        BoeMessage message = new BoeMessage(new byte[]{(byte) 0xBA, (byte) 0xBA, 0x05, 0x00, 0x01});

        // Act
        MessageValidator.ValidationResult result = MessageValidator.validate(message);

        // Assert
        assertFalse(result.isValid());
        assertTrue(result.getMessage().startsWith("Length field mismatch"));
    }
}