package com.boe.simulator.test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Mock BOE Server for testing purposes
 * Simulates a basic BOE server that accepts connections and responds to messages
 */
public class MockBoeServer {
    private static final Logger LOGGER = Logger.getLogger(MockBoeServer.class.getName());
    private static final int DEFAULT_PORT = 12345;

    // Message Types
    private static final byte LOGIN_REQUEST = 0x37;
    private static final byte LOGOUT_REQUEST = 0x02;
    private static final byte HEARTBEAT = 0x03;
    private static final byte LOGIN_RESPONSE = 0x07;  // Example response type

    // Start of Message marker
    private static final byte START_OF_MESSAGE_1 = (byte) 0xBA;
    private static final byte START_OF_MESSAGE_2 = (byte) 0xBA;

    private final int port;
    private final ExecutorService executor;
    private ServerSocket serverSocket;
    private volatile boolean running;

    public MockBoeServer() {
        this(DEFAULT_PORT);
    }

    public MockBoeServer(int port) {
        this.port = port;
        this.executor = Executors.newCachedThreadPool();
        this.running = false;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(port);
        running = true;

        LOGGER.info("╔════════════════════════════════════════════════╗");
        LOGGER.info("║     Mock BOE Server Started                    ║");
        LOGGER.info("║     Listening on port: " + port + "                   ║");
        LOGGER.info("║     Press Ctrl+C to stop                       ║");
        LOGGER.info("╚════════════════════════════════════════════════╝");

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();
                LOGGER.info("✓ Client connected: " + clientSocket.getRemoteSocketAddress());

                executor.submit(() -> handleClient(clientSocket));

            } catch (IOException e) {
                if (running)LOGGER.log(Level.SEVERE, "Error accepting connection", e);
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        boolean clientActive = true;

        try (Socket socket = clientSocket) {
            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            LOGGER.info("Client handler started for: " + socket.getRemoteSocketAddress());

            while (!socket.isClosed() && running && clientActive) {
                try {
                    byte[] message = readMessage(input);

                    if (message == null) {
                        LOGGER.info("Client disconnected: " + socket.getRemoteSocketAddress());
                        break;
                    }

                    // Check if it's a logout request
                    if (message.length >= 5 && message[4] == LOGOUT_REQUEST) {
                        processMessage(message, output);
                        LOGGER.info("Client logout received, closing connection gracefully");
                        clientActive = false; // Exit loop after logout
                    } else {
                        processMessage(message, output);
                    }

                } catch (IOException e) {
                    if (running) {
                        LOGGER.log(Level.WARNING, "Error reading message from client", e);
                    }
                    break;
                }
            }

            LOGGER.info("Client handler finished for: " + socket.getRemoteSocketAddress());

        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error handling client", e);
        }
    }

    private byte[] readMessage(InputStream input) throws IOException {
        // Read Start of Message (2 bytes)
        byte[] startMarker = new byte[2];
        int bytesRead = input.read(startMarker);

        if (bytesRead < 0) return null; // End of stream
        if (bytesRead < 2 || startMarker[0] != START_OF_MESSAGE_1 || startMarker[1] != START_OF_MESSAGE_2) {
            LOGGER.warning("Invalid start of message marker: " + (bytesRead >= 2 ? String.format("0x%02X%02X", startMarker[0], startMarker[1]) : "incomplete"));
            return null;
        }

        // Read Message Length (2 bytes)
        byte[] lengthBytes = new byte[2];
        readFully(input, lengthBytes, 0, 2);

        ByteBuffer lengthBuffer = ByteBuffer.wrap(lengthBytes).order(ByteOrder.LITTLE_ENDIAN);
        int messageLength = lengthBuffer.getShort() & 0xFFFF;

        if (messageLength < 2 || messageLength > 65535) {
            LOGGER.warning("Invalid message length: " + messageLength);
            return null;
        }

        // MessageLength includes itself (2 bytes), so payload = messageLength - 2
        int payloadLength = messageLength - 2;

        // Read the payload
        byte[] payload = new byte[payloadLength];
        if (payloadLength > 0) readFully(input, payload, 0, payloadLength);

        // Reconstruct full message: StartOfMessage(2) + MessageLength(2) + Payload
        byte[] fullMessage = new byte[2 + messageLength];
        System.arraycopy(startMarker, 0, fullMessage, 0, 2);
        System.arraycopy(lengthBytes, 0, fullMessage, 2, 2);
        if (payloadLength > 0) System.arraycopy(payload, 0, fullMessage, 4, payloadLength);

        return fullMessage;
    }

    private void readFully(InputStream input, byte[] buffer, int offset, int length) throws IOException {
        int totalRead = 0;
        while (totalRead < length) {
            int count = input.read(buffer, offset + totalRead, length - totalRead);
            if (count < 0) throw new IOException("Unexpected end of stream");
            totalRead += count;
        }
    }

    private void processMessage(byte[] message, OutputStream output) throws IOException {
        if (message.length < 5) {
            LOGGER.warning("Message too short");
            return;
        }

        // Extract message type (index 4)
        byte messageType = message[4];

        LOGGER.info("Received message type: 0x" + String.format("%02X", messageType));

        switch (messageType) {
            case LOGIN_REQUEST: handleLoginRequest(message, output);
            break;

            case LOGOUT_REQUEST: handleLogoutRequest(message, output);
            break;

            case HEARTBEAT: handleHeartbeat(message, output);
            break;

            default: LOGGER.warning("Unknown message type: 0x" + String.format("%02X", messageType));
            break;
        }
    }

    private void handleLoginRequest(byte[] message, OutputStream output) throws IOException {
        LOGGER.info("Processing Login Request...");

        // Extract username (assuming offset 10, length 4)
        if (message.length >= 14) {
            String username = extractString(message, 10, 4);
            LOGGER.info("  Username: '" + username + "'");
        }

        // Send Login Response (simplified)
        byte[] response = createLoginResponse();
        output.write(response);
        output.flush();

        LOGGER.info("✓ Login Response sent");
    }

    private void handleLogoutRequest(byte[] message, OutputStream output) throws IOException {
        LOGGER.info("Processing Logout Request...");

        // Send a simple logout acknowledgment (optional)
        byte[] response = createLogoutResponse();
        output.write(response);
        output.flush();

        LOGGER.info("✓ Logout Response sent, client will disconnect");
    }

    private byte[] createLogoutResponse() {
        // Simple Logout Response: StartOfMessage + Length + Type + MatchingUnit + SequenceNumber
        int messageLength = 6; // Type(1) + MatchingUnit(1) + SequenceNumber(4)

        ByteBuffer buffer = ByteBuffer.allocate(4 + messageLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put((byte) 0x08); // Logout Response type (example)
        buffer.put((byte) 0); // Matching Unit
        buffer.putInt(1); // Sequence Number

        return buffer.array();
    }

    private void handleHeartbeat(byte[] message, OutputStream output) throws IOException {
        LOGGER.fine("Processing Heartbeat...");

        // Echo back a heartbeat response
        output.write(message);
        output.flush();

        LOGGER.fine("✓ Heartbeat response sent");
    }

    private byte[] createLoginResponse() {
        // Simple Login Response: StartOfMessage + Length + Type + MatchingUnit + SequenceNumber
        int messageLength = 6; // Type(1) + MatchingUnit(1) + SequenceNumber(4)

        ByteBuffer buffer = ByteBuffer.allocate(4 + messageLength);
        buffer.order(ByteOrder.LITTLE_ENDIAN);

        buffer.put(START_OF_MESSAGE_1);
        buffer.put(START_OF_MESSAGE_2);
        buffer.putShort((short) messageLength);
        buffer.put(LOGIN_RESPONSE);
        buffer.put((byte) 0); // Matching Unit
        buffer.putInt(1); // Sequence Number

        return buffer.array();
    }

    private String extractString(byte[] data, int offset, int length) {
        if (offset + length > data.length) return "";

        byte[] strBytes = new byte[length];
        System.arraycopy(data, offset, strBytes, 0, length);

        // Trim spaces and null bytes
        String str = new String(strBytes).trim();
        return str.replace("\0", "");
    }

    public void stop() {
        running = false;

        try {
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();

        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error closing server socket", e);
        }

        executor.shutdown();
        LOGGER.info("Mock BOE Server stopped");
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;

        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port number. Using default: " + DEFAULT_PORT);
            }
        }

        MockBoeServer server = new MockBoeServer(port);

        // Add shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down server...");
            server.stop();
        }));

        try {
            server.start();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to start server", e);
            System.exit(1);
        }
    }
}