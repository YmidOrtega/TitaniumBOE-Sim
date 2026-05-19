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

    // Reused per-instance: safe because each ClientConnectionHandler owns its own serializer
    // and calls deserialize() from a single virtual thread.
    private final byte[] headerBuf = new byte[HEADER_SIZE];

    public byte[] serialize(BoeMessage message) {
        if (message == null || message.getData() == null) throw new IllegalArgumentException("Message cannot be null");
        return message.getData();
    }

    public byte[] serialize(byte[] payload) {
        if (payload == null || payload.length == 0) throw new IllegalArgumentException("Payload cannot be null or empty");

        int messageLength = 2 + payload.length;
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
        // Read the 4-byte header in one call (avoids 2 small byte[] allocations and an extra read)
        readFully(inputStream, headerBuf, 0, HEADER_SIZE);

        if (headerBuf[0] != START_OF_MESSAGE_1 || headerBuf[1] != START_OF_MESSAGE_2)
            throw new IOException("Invalid start of message marker: 0x"
                    + String.format("%02X%02X", headerBuf[0], headerBuf[1]));

        // Little-endian decode without allocating a ByteBuffer
        int messageLength = (headerBuf[2] & 0xFF) | ((headerBuf[3] & 0xFF) << 8);
        if (messageLength < 2) throw new IOException("Invalid message length: " + messageLength);

        int payloadLength = messageLength - 2;

        // Allocate one array and read the payload directly into it — no intermediate messageBody
        byte[] fullMessage = new byte[HEADER_SIZE + payloadLength];
        fullMessage[0] = headerBuf[0];
        fullMessage[1] = headerBuf[1];
        fullMessage[2] = headerBuf[2];
        fullMessage[3] = headerBuf[3];
        readFully(inputStream, fullMessage, HEADER_SIZE, payloadLength);

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
