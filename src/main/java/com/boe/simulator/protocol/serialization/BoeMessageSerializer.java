package com.boe.simulator.protocol.serialization;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import com.boe.simulator.protocol.message.BoeMessage;

public class BoeMessageSerializer {
    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;
    private static final int HEADER_SIZE = 4;

    public byte[] serialize(BoeMessage message) {
        if (message == null || message.getData() == null) throw new IllegalArgumentException("Message cannot be null");
        return message.getData();
    }

    public byte[] serialize(byte[] payload) {
        if (payload == null || payload.length == 0) throw new IllegalArgumentException("Payload cannot be null or empty");

        // Calculate message length according to BOE spec:
        // MessageLength = 2 (length field itself) + payload length
        int messageLength = 2 + payload.length;
        
        // Total bytes to send = StartMarker(2) + MessageLength(2 + payload)
        int totalLength = 2 + messageLength;

        if (messageLength > 0xFFFF) throw new IllegalArgumentException("Message too large: " + messageLength + " bytes");

        ByteBuffer buffer = ByteBuffer.allocate(totalLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put(payload);

        return buffer.array();
    }

    public BoeMessage deserialize(InputStream inputStream) throws IOException {
        byte[] startMarker = new byte[2];
        readFully(inputStream, startMarker, 0, 2);

        if (startMarker[0] != START_OF_MESSAGE_1 || startMarker[1] != START_OF_MESSAGE_2) throw new IOException("Invalid start of message marker: 0x" + String.format("%02X%02X", startMarker[0], startMarker[1]));

        byte[] lengthBytes = new byte[2];
        readFully(inputStream, lengthBytes, 0, 2);

        ByteBuffer lengthBuffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = lengthBuffer.getShort() & 0xFFFF;

        if (messageLength < 2) throw new IOException("Invalid message length: " + messageLength);

        // Payload size = MessageLength - 2 (excluding the length field itself)
        int payloadLength = messageLength - 2;

        byte[] messageBody = new byte[payloadLength];
        readFully(inputStream, messageBody, 0, payloadLength);

        // Rebuild full message = Header(4) + Payload
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
