package com.boe.simulator.protocol.message;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BoeMessageFactory {
    private static final Logger LOGGER = Logger.getLogger(BoeMessageFactory.class.getName());

    // Message type constants - Session messages
    public static final byte LOGIN_REQUEST = 0x37;
    public static final byte LOGOUT_REQUEST = 0x02;
    public static final byte CLIENT_HEARTBEAT = 0x03;
    public static final byte SERVER_HEARTBEAT = 0x04;
    public static final byte LOGIN_RESPONSE = 0x07;
    public static final byte LOGOUT_RESPONSE = 0x08;

    // Message type constants - Order messages
    public static final byte NEW_ORDER = 0x38;
    public static final byte CANCEL_ORDER = 0x39;
    public static final byte ORDER_EXECUTED = 0x21;  // ⭐ NUEVO
    public static final byte ORDER_ACKNOWLEDGMENT = 0x25;
    public static final byte ORDER_REJECTED = 0x26;
    public static final byte ORDER_CANCELLED = 0x28;

    public enum Context {
        CLIENT,
        SERVER
    }

    public static Object createMessage(BoeMessage message) {
        byte messageType = message.getMessageType();
        Context context = isRequest(messageType) ? Context.SERVER : Context.CLIENT;
        return createMessage(message, context);
    }

    public static Object createMessage(BoeMessage message, Context context) {
        byte messageType = message.getMessageType();
        byte[] data = message.getData();

        LOGGER.log(Level.FINE, "Creating message type 0x{0} ({1}) in {2} context",
                new Object[]{String.format("%02X", messageType), getMessageTypeName(messageType), context});

        try {
            return switch (messageType) {
                // Session messages
                case SERVER_HEARTBEAT -> new ServerHeartbeatMessage(data);
                case LOGIN_RESPONSE -> new LoginResponseMessage(data);
                case LOGOUT_RESPONSE -> new LogoutResponseMessage(data);
                case LOGIN_REQUEST -> parseLoginRequest(data);
                case LOGOUT_REQUEST -> parseLogoutRequest(data);
                case CLIENT_HEARTBEAT -> parseClientHeartbeat(data);

                // Order messages (inbound from client - server receives these)
                case NEW_ORDER -> NewOrderMessage.parse(data);
                case CANCEL_ORDER -> CancelOrderMessage.parse(data);

                // Order messages (outbound to client - client receives these)
                case ORDER_EXECUTED -> {
                    if (context == Context.SERVER) {
                        LOGGER.warning("Server received outbound-only message: OrderExecuted");
                        yield null;
                    }
                    yield OrderExecutedMessage.fromBytes(data);
                }

                case ORDER_ACKNOWLEDGMENT -> {
                    if (context == Context.SERVER) {
                        LOGGER.warning("Server received outbound-only message: OrderAcknowledgment");
                        yield null;
                    }
                    yield OrderAcknowledgmentMessage.fromBytes(data);
                }

                case ORDER_REJECTED -> {
                    if (context == Context.SERVER) {
                        LOGGER.warning("Server received outbound-only message: OrderRejected");
                        yield null;
                    }
                    yield OrderRejectedMessage.fromBytes(data);
                }

                case ORDER_CANCELLED -> {
                    if (context == Context.SERVER) {
                        LOGGER.warning("Server received outbound-only message: OrderCancelled");
                        yield null;
                    }
                    yield OrderCancelledMessage.fromBytes(data);
                }

                default -> {
                    LOGGER.log(Level.WARNING, "Unknown message type: 0x{0}", String.format("%02X", messageType));
                    yield null;
                }
            };
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to parse message type 0x{0} ({1}): {2}",
                    new Object[]{String.format("%02X", messageType), getMessageTypeName(messageType), e.getMessage()});
            return null;
        }
    }

    private static LoginRequestMessage parseLoginRequest(byte[] data) {
        return LoginRequestMessage.parseFromBytes(data);
    }

    private static LogoutRequestMessage parseLogoutRequest(byte[] data) {
        return LogoutRequestMessage.parseFromBytes(data);
    }

    private static ClientHeartbeatMessage parseClientHeartbeat(byte[] data) {
        return ClientHeartbeatMessage.parseFromBytes(data);
    }

    public static String getMessageTypeName(byte messageType) {
        return switch (messageType) {
            // Session messages
            case LOGIN_REQUEST -> "LoginRequest";
            case LOGOUT_REQUEST -> "LogoutRequest";
            case CLIENT_HEARTBEAT -> "ClientHeartbeat";
            case SERVER_HEARTBEAT -> "ServerHeartbeat";
            case LOGIN_RESPONSE -> "LoginResponse";
            case LOGOUT_RESPONSE -> "LogoutResponse";

            // Order messages
            case NEW_ORDER -> "NewOrder";
            case CANCEL_ORDER -> "CancelOrder";
            case ORDER_EXECUTED -> "OrderExecuted";
            case ORDER_ACKNOWLEDGMENT -> "OrderAcknowledgment";
            case ORDER_REJECTED -> "OrderRejected";
            case ORDER_CANCELLED -> "OrderCancelled";

            default -> "Unknown(0x" + String.format("%02X", messageType) + ")";
        };
    }

    public static boolean isRequest(byte messageType) {
        return messageType == LOGIN_REQUEST ||
                messageType == LOGOUT_REQUEST ||
                messageType == CLIENT_HEARTBEAT ||
                messageType == NEW_ORDER ||
                messageType == CANCEL_ORDER;
    }

    public static boolean isResponse(byte messageType) {
        return messageType == LOGIN_RESPONSE ||
                messageType == LOGOUT_RESPONSE ||
                messageType == SERVER_HEARTBEAT ||
                messageType == ORDER_EXECUTED ||
                messageType == ORDER_ACKNOWLEDGMENT ||
                messageType == ORDER_REJECTED ||
                messageType == ORDER_CANCELLED;
    }

    public static boolean isOrderMessage(byte messageType) {
        return messageType == NEW_ORDER ||
                messageType == CANCEL_ORDER ||
                messageType == ORDER_EXECUTED ||
                messageType == ORDER_ACKNOWLEDGMENT ||
                messageType == ORDER_REJECTED ||
                messageType == ORDER_CANCELLED;
    }
}