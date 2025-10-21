package com.boe.simulator.protocol.serialization;

import com.boe.simulator.protocol.message.BoeMessage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class BoeMessageSerializer {
    private static final short MESSAGE_LENGTH_FIELD_SIZE = 2;

    public byte[] serialize(BoeMessage message) {
        return message.getData();
    }

    public byte[] serialize(byte[] payload) {
        int totalLength = MESSAGE_LENGTH_FIELD_SIZE + payload.length;
        
        if (totalLength > 0xFFFF) throw new IllegalArgumentException("Message too large: " + totalLength + " bytes");
        
        byte[] message = new byte[totalLength];
        
        ByteBuffer buffer = ByteBuffer.wrap(message, 0, MESSAGE_LENGTH_FIELD_SIZE).order(ByteOrder.LITTLE_ENDIAN);
        buffer.putShort((short) totalLength);
        
        System.arraycopy(payload, 0, message, MESSAGE_LENGTH_FIELD_SIZE, payload.length);
        
        return message;
    }

    public BoeMessage deserialize(InputStream inputStream) throws IOException {
        byte[] lengthBytes = new byte[MESSAGE_LENGTH_FIELD_SIZE];
        readFully(inputStream, lengthBytes, 0, MESSAGE_LENGTH_FIELD_SIZE);
        
        ByteBuffer lengthBuffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = lengthBuffer.getShort() & 0xFFFF;
        
        if (messageLength < MESSAGE_LENGTH_FIELD_SIZE) throw new IOException("Invalid message length: " + messageLength);
        
        byte[] messageData = new byte[messageLength];
        System.arraycopy(lengthBytes, 0, messageData, 0, MESSAGE_LENGTH_FIELD_SIZE);
        
        int remainingLength = messageLength - MESSAGE_LENGTH_FIELD_SIZE;
        if (remainingLength > 0) readFully(inputStream, messageData, MESSAGE_LENGTH_FIELD_SIZE, remainingLength);
        
        return new BoeMessage(messageData);
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
