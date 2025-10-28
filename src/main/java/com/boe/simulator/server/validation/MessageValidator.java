package com.boe.simulator.server.validation;

import com.boe.simulator.protocol.message.BoeMessage;

public class MessageValidator {
    private static final int MIN_MESSAGE_LENGTH = 4;
    private static final int MAX_MESSAGE_LENGTH = 65535;

    public static ValidationResult validate(BoeMessage message) {
        if (message == null) return ValidationResult.invalid("Message is null");
        
        // Check valid start marker
        if (!message.hasValidStartMarker()) return ValidationResult.invalid("Invalid start marker");
        
        // Check message length
        int length = message.getLength();

        if (length < MIN_MESSAGE_LENGTH) return ValidationResult.invalid("Message too short: " + length + " bytes");
        if (length > MAX_MESSAGE_LENGTH) return ValidationResult.invalid("Message too long: " + length + " bytes");
    
        // Check length field consistency
        int lengthField = message.getLengthField();
        int expectedPayloadLength = length - 4;
        
        if (lengthField != expectedPayloadLength + 2) return ValidationResult.invalid("Length field mismatch: field=" + lengthField + ", expected=" + (expectedPayloadLength + 2));
        
        return ValidationResult.valid();
    }

    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        
        private ValidationResult(boolean valid, String message) {
            this.valid = valid;
            this.message = message;
        }
        
        public static ValidationResult valid() {
            return new ValidationResult(true, null);
        }
        
        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message);
        }
        
        public boolean isValid() {
            return valid;
        }
        
        public String getMessage() {
            return message;
        }
    }
}