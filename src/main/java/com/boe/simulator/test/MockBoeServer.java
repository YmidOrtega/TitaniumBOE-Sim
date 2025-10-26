package com.boe.simulator.test;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

public class MockBoeServer {

    private static final Logger LOGGER = Logger.getLogger(MockBoeServer.class.getName());
    private static final int PORT = 12345;

    // BOE Constants
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private static final byte CLIENT_HEARTBEAT = 0x03;
    private static final byte SERVER_HEARTBEAT = 0x04;
    private static final byte LOGIN_REQUEST = 0x37;
    private static final byte LOGIN_RESPONSE = 0x07;
    private static final byte LOGOUT_REQUEST = 0x02;

    private final ExecutorService clientExecutor = Executors.newCachedThreadPool();

    public void start() {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            LOGGER.info("✅ MockBoeServer started on port " + PORT);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("✓ Client connected: " + clientSocket.getRemoteSocketAddress());
                clientExecutor.submit(() -> handleClient(clientSocket));
            }
        } catch (IOException e) {
            LOGGER.severe("Server error: " + e.getMessage());
        }
    }

    private void handleClient(Socket clientSocket) {
        try (InputStream input = clientSocket.getInputStream();
             OutputStream output = clientSocket.getOutputStream()) {

            LOGGER.info("Client handler started for: " + clientSocket.getRemoteSocketAddress());

            while (!clientSocket.isClosed()) {
                byte[] message = readMessage(input);
                if (message == null || message.length == 0) continue;

                LOGGER.info("Message received (" + message.length + " bytes): " + bytesToHex(message));
                processMessage(message, output);
            }

        } catch (EOFException eof) {
            LOGGER.info("Client disconnected gracefully: " + clientSocket.getRemoteSocketAddress());
        } catch (IOException e) {
            LOGGER.warning("Client connection error: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException ignored) {}
            LOGGER.info("Client handler terminated for: " + clientSocket.getRemoteSocketAddress());
        }
    }

    /**
     * Reads a complete BOE message based on the protocol structure.
     */
    private byte[] readMessage(InputStream input) throws IOException {
        // Buscar el marcador de inicio 0xBA 0xBA
        int b1, b2;
        while (true) {
            b1 = input.read();
            if (b1 == -1) return null;
            if (b1 != 0xBA) continue;

            b2 = input.read();
            if (b2 == -1) return null;
            if (b2 == 0xBA) break;
        }

        // Leer longitud (2 bytes, little endian)
        byte[] lenBytes = readFully(input, 2);
        int payloadLength = ByteBuffer.wrap(lenBytes).order(ByteOrder.LITTLE_ENDIAN).getShort() & 0xFFFF;

        // Leer payload completo
        byte[] payload = readFully(input, payloadLength);

        // Armar mensaje completo (SOM + length + payload)
        ByteBuffer buffer = ByteBuffer.allocate(4 + payloadLength);
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.put(lenBytes);
        buffer.put(payload);

        LOGGER.info(String.format("✅ Received complete message (%d bytes)", buffer.array().length));
        return buffer.array();
    }

    /**
     * Reads exactly 'length' bytes from InputStream
     */
    private byte[] readFully(InputStream input, int length) throws IOException {
        byte[] buffer = new byte[length];
        int read = 0;
        while (read < length) {
            int n = input.read(buffer, read, length - read);
            if (n == -1) throw new EOFException("Stream closed before reading fully");
            read += n;
        }
        return buffer;
    }

    /**
     * Process a BOE message by type.
     */
    private void processMessage(byte[] message, OutputStream output) throws IOException {
        if (message.length < 5) {
            LOGGER.warning("Message too short to process.");
            return;
        }

        byte messageType = message[4];
        LOGGER.info(String.format("Received message type: 0x%02X", messageType));

        switch (messageType) {
            case LOGIN_REQUEST:
                LOGGER.info("✅ Received Login Request");
                sendLoginResponse(output);
                break;

            case CLIENT_HEARTBEAT:
                LOGGER.info("💓 Received Client Heartbeat");
                sendServerHeartbeat(output);
                break;

            case LOGOUT_REQUEST:
                LOGGER.info("👋 Received Logout Request");
                break;

            default:
                LOGGER.warning(String.format("Unknown message type: 0x%02X", messageType));
                break;
        }
    }

    private void sendLoginResponse(OutputStream output) throws IOException {
        byte[] response = createLoginResponse();  // Usar el método correcto
        output.write(response);
        output.flush();
        LOGGER.info("✅ Sent Login Response (0x07) - " + response.length + " bytes: " + bytesToHex(response, 20));
    }

    private void sendServerHeartbeat(OutputStream output) throws IOException {
        byte[] heartbeat = buildServerHeartbeat();
        output.write(heartbeat);
        output.flush();
        LOGGER.info("✅ Sent Server Heartbeat (0x04): " + bytesToHex(heartbeat, heartbeat.length));
    }

    private byte[] buildLoginResponse() {
        int payloadLength = 6; // MessageType + MatchingUnit + SequenceNumber(4)
        ByteBuffer buffer = ByteBuffer.allocate(4 + payloadLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) payloadLength);
        buffer.put(LOGIN_RESPONSE);  // Message Type
        buffer.put((byte) 0x00);     // Matching Unit
        buffer.putInt(1);            // Sequence Number
        return buffer.array();
    }

    private byte[] buildServerHeartbeat() {
        int payloadLength = 6; // MessageType + MatchingUnit + SequenceNumber(4)
        ByteBuffer buffer = ByteBuffer.allocate(4 + payloadLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) payloadLength);
        buffer.put(SERVER_HEARTBEAT); // Message Type
        buffer.put((byte) 0x00);      // Matching Unit
        buffer.putInt(1);             // Sequence Number
        return buffer.array();
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02X ", b));
        return sb.toString().trim();
    }

    public static void main(String[] args) {
        new MockBoeServer().start();
    }

    private byte[] createLoginResponse() {
        // Login Response structure según BOE spec
        int payloadLength = 72;  // 1+1+4+1+60+4+1

        ByteBuffer buffer = ByteBuffer.allocate(4 + payloadLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) payloadLength);
        buffer.put(LOGIN_RESPONSE);
        buffer.put((byte) 0);
        buffer.putInt(1);
        buffer.put((byte) 'A');

        // Text (60 bytes)
        String text = "Login successful";
        byte[] textBytes = new byte[60];
        java.util.Arrays.fill(textBytes, (byte) 0x20);
        byte[] msgBytes = text.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        System.arraycopy(msgBytes, 0, textBytes, 0, Math.min(msgBytes.length, 60));
        buffer.put(textBytes);

        buffer.putInt(0);
        buffer.put((byte) 1);

        return buffer.array();
    }

    private String bytesToHex(byte[] bytes, int maxLength) {
        StringBuilder sb = new StringBuilder();
        int len = Math.min(bytes.length, maxLength);
        for (int i = 0; i < len; i++) {
            sb.append(String.format("%02X ", bytes[i]));
        }
        if (bytes.length > maxLength) {
            sb.append("...");
        }
        return sb.toString().trim();
    }
}
