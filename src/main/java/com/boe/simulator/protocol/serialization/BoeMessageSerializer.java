package com.boe.simulator.protocol.serialization;

import com.boe.simulator.protocol.message.BoeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BoeMessageSerializer {
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    private static final int HEADER_SIZE = 4;

    public byte[] serialize(BoeMessage message) {
        return message.getData();
    }

    public byte[] serialize(byte[] payload) {
        if (payload == null || payload.length == 0) throw new IllegalArgumentException("Payload cannot be null or empty");
    
        // MessageLength = 2 (length field itself) + payload length
        int messageLength = 2 + payload.length;
        
        // Total message = StartOfMessage(2) + MessageLength(2) + Payload
        int totalLength = 2 + messageLength;
        
        if (messageLength > 0xFFFF) throw new IllegalArgumentException("Message too large: " + messageLength + " bytes");
        
        byte[] message = new byte[totalLength];
        ByteBuffer buffer = ByteBuffer.wrap(message).order(ByteOrder.LITTLE_ENDIAN);
        
        // Start of Message (2 bytes)
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        
        // Message Length (2 bytes) - includes itself + payload
        buffer.putShort((short) messageLength);
        
        // Payload
        buffer.put(payload);
        
        return message;
    }

    public BoeMessage deserialize(InputStream inputStream) throws IOException {
        // Read Start of Message (2 bytes)
        byte[] startMarker = new byte[2];
        readFully(inputStream, startMarker, 0, 2);
        
        if (startMarker[0] != START_OF_MESSAGE_1 || startMarker[1] != START_OF_MESSAGE_2) throw new IOException("Invalid start of message marker: 0x" + String.format("%02X%02X", startMarker[0], startMarker[1]));
        
        // Read Message Length (2 bytes)
        byte[] lengthBytes = new byte[2];
        readFully(inputStream, lengthBytes, 0, 2);
        
        ByteBuffer lengthBuffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = lengthBuffer.getShort() & 0xFFFF;

        final int MIN_PROCESSABLE_LENGTH = 8;
        
        if (messageLength < MIN_PROCESSABLE_LENGTH) throw new IOException("Invalid message length: " + messageLength);

        int payloadLength = messageLength - 2;
        
        // Read message body
        byte[] messageBody = new byte[payloadLength];
        readFully(inputStream, messageBody, 0, payloadLength);
        
        // Reconstruct full message including header
        byte[] fullMessage = new byte[HEADER_SIZE + payloadLength];
        System.arraycopy(startMarker, 0, fullMessage, 0, 2);
        System.arraycopy(lengthBytes, 0, fullMessage, 2, 2);
        System.arraycopy(messageBody, 0, fullMessage, HEADER_SIZE, payloadLength);
        
        return new BoeMessage(fullMessage);
    }

    private void readFully(InputStream inputStream, byte[] buffer, int offset, int length) throws IOException {
        int bytesRead = 0;
        while (bytesRead < length) {
            int count = inputStream.read(buffer, offset + bytesRead, length - bytesRead);
            if (count < 0) throw new IOException("End of stream reached before reading fully.");
            
            bytesRead += count;
        }
    }
}