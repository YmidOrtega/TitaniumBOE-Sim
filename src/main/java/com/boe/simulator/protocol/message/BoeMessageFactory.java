package com.boe.simulator.protocol.message;

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

    public static Object createMessage(BoeMessage message) {
        byte messageType = message.getMessageType();
        byte[] data = message.getData();

        try {
            switch (messageType) {
                case SERVER_HEARTBEAT: return new ServerHeartbeatMessage(data);
                case LOGIN_RESPONSE: return new LoginResponseMessage(data);
                case LOGOUT_RESPONSE: return new LogoutResponseMessage(data);

                // Add more message types as needed

                default: LOGGER.log(Level.WARNING, "Unknown message type: 0x{0}", String.format("%02X", messageType));
                return null;
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to deserialize message type 0x{0}: {1}", new Object[]{String.format("%02X", messageType), e.getMessage()});
            return null;
        }
    }

    public static String getMessageTypeName(byte messageType) {
        switch (messageType) {
            case LOGIN_REQUEST: return "LoginRequest";
            case LOGOUT_REQUEST: return "LogoutRequest";
            case CLIENT_HEARTBEAT: return "ClientHeartbeat";
            case SERVER_HEARTBEAT: return "ServerHeartbeat";
            case LOGIN_RESPONSE: return "LoginResponse";
            case LOGOUT_RESPONSE: return "LogoutResponse";
            default: return "Unknown(0x" + String.format("%02X", messageType) + ")";
        }
    }

    public static boolean isRequest(byte messageType) {
        return messageType == LOGIN_REQUEST || messageType == LOGOUT_REQUEST || messageType == CLIENT_HEARTBEAT;
    }

    public static boolean isResponse(byte messageType) {
        return messageType == LOGIN_RESPONSE || messageType == LOGOUT_RESPONSE || messageType == SERVER_HEARTBEAT;
    }
}