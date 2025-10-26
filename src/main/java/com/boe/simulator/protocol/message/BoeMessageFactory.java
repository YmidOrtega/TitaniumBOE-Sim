package com.boe.simulator.protocol.message;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BoeMessageFactory {
    private static final Logger LOGGER = Logger.getLogger(BoeMessageFactory.class.getName());

    // Message type constants
    public static final byte LOGIN_REQUEST = 0x37;
    public static final byte LOGOUT_REQUEST = 0x02;
    public static final byte CLIENT_HEARTBEAT = 0x03;
    public static final byte SERVER_HEARTBEAT = 0x04;
    public static final byte LOGIN_RESPONSE = 0x07;
    public static final byte LOGOUT_RESPONSE = 0x08;

    private static final int HEADER_SIZE = 5; // StartOfMessage(2) + MessageLength(2) + MessageType(1)

    public static Object createMessage(BoeMessage message) {
        byte messageType = message.getMessageType();
        byte[] data = message.getData();

        LOGGER.log(Level.INFO, "Received message type 0x{0} ({1}), length={2}", new Object[]{String.format("%02X", messageType), getMessageTypeName(messageType), data.length});

        try {
            switch (messageType) {
                case SERVER_HEARTBEAT -> {
                    return new ServerHeartbeatMessage(data);
                }
                case LOGIN_RESPONSE -> {
                    return new LoginResponseMessage(data);
                }
                case LOGOUT_RESPONSE -> {
                    return new LogoutResponseMessage(data);
                }
                case LOGIN_REQUEST -> {
                    return parseLoginRequest(data);
                }
                case LOGOUT_REQUEST -> {
                    return parseLogoutRequest(data);
                }
                case CLIENT_HEARTBEAT -> {
                    return parseClientHeartbeat(data);
                }

                default -> {
                    LOGGER.log(Level.WARNING, "Unknown message type: 0x{0}", String.format("%02X", messageType));
                    return null;
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to parse message type 0x{0} ({1}): {2}", new Object[]{String.format("%02X", messageType), getMessageTypeName(messageType), e.getMessage()});
            return null;
        }
    }

    private static LoginRequestMessage parseLoginRequest(byte[] data) {
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        
        // Skip: StartOfMessage(2) + MessageLength(2) + MessageType(1) = 5 bytes
        buffer.position(5);
        
        // Read MatchingUnit (1 byte)
        byte matchingUnit = buffer.get();
        
        // Read SequenceNumber (4 bytes)
        int sequenceNumber = buffer.getInt();
        
        // Read SessionSubID (4 bytes)
        byte[] sessionSubIDBytes = new byte[4];
        buffer.get(sessionSubIDBytes);
        String sessionSubID = new String(sessionSubIDBytes, StandardCharsets.US_ASCII).trim();
        
        // Read Username (4 bytes)
        byte[] usernameBytes = new byte[4];
        buffer.get(usernameBytes);
        String username = new String(usernameBytes, StandardCharsets.US_ASCII).trim();
        
        // Read Password (10 bytes)
        byte[] passwordBytes = new byte[10];
        buffer.get(passwordBytes);
        String password = new String(passwordBytes, StandardCharsets.US_ASCII).trim();
        
        LoginRequestMessage loginRequest = new LoginRequestMessage(username, password, sessionSubID, matchingUnit);
        loginRequest.setSequenceNumber(sequenceNumber);
        
        return loginRequest;
    }

    private static LogoutRequestMessage parseLogoutRequest(byte[] data) {
        int expectedLength = HEADER_SIZE + 1 + 4;
        if (data.length < expectedLength) {
            throw new IllegalArgumentException("Invalid LogoutRequest length: expected >= " + expectedLength + ", got " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE);

        byte matchingUnit = buffer.get();
        int sequenceNumber = buffer.getInt();

        LOGGER.log(Level.INFO, "Parsed LogoutRequest: MU={0}, seq={1}", new Object[]{matchingUnit, sequenceNumber});
        return new LogoutRequestMessage(matchingUnit, sequenceNumber);
    }

    private static ClientHeartbeatMessage parseClientHeartbeat(byte[] data) {
        int expectedLength = HEADER_SIZE + 1 + 4;
        if (data.length < expectedLength) {
            throw new IllegalArgumentException("Invalid ClientHeartbeat length: expected >= " + expectedLength + ", got " + data.length);
        }

        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        buffer.position(HEADER_SIZE);

        byte matchingUnit = buffer.get();
        int sequenceNumber = buffer.getInt();

        LOGGER.log(Level.INFO, "Parsed ClientHeartbeat: MU={0}, seq={1}", new Object[]{matchingUnit, sequenceNumber});
        return new ClientHeartbeatMessage(matchingUnit, sequenceNumber);
    }

    public static String getMessageTypeName(byte messageType) {
        return switch (messageType) {
            case LOGIN_REQUEST -> "LoginRequest";
            case LOGOUT_REQUEST -> "LogoutRequest";
            case CLIENT_HEARTBEAT -> "ClientHeartbeat";
            case SERVER_HEARTBEAT -> "ServerHeartbeat";
            case LOGIN_RESPONSE -> "LoginResponse";
            case LOGOUT_RESPONSE -> "LogoutResponse";
            default -> "Unknown(0x" + String.format("%02X", messageType) + ")";
        };
    }

    public static boolean isRequest(byte messageType) {
        return messageType == LOGIN_REQUEST || messageType == LOGOUT_REQUEST || messageType == CLIENT_HEARTBEAT;
    }

    public static boolean isResponse(byte messageType) {
        return messageType == LOGIN_RESPONSE || messageType == LOGOUT_RESPONSE || messageType == SERVER_HEARTBEAT;
    }
}