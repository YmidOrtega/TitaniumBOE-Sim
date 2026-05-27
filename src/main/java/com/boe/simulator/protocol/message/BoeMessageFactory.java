package com.boe.simulator.protocol.message;

import java.util.logging.Level;
import java.util.logging.Logger;

public class BoeMessageFactory {
    private static final Logger LOGGER = Logger.getLogger(BoeMessageFactory.class.getName());

    // Message type constants - Session messages (per spec v2.11.90)
    public static final byte LOGIN_REQUEST  = 0x37;
    public static final byte LOGOUT_REQUEST = 0x02;
    public static final byte CLIENT_HEARTBEAT = 0x03;
    public static final byte LOGIN_RESPONSE = 0x24;
    public static final byte LOGOUT_RESPONSE = 0x08;
    public static final byte SERVER_HEARTBEAT = 0x09;
    public static final byte REPLAY_COMPLETE = 0x13;

    // Message type constants - Order messages (inbound)
    public static final byte NEW_ORDER    = 0x38;
    public static final byte CANCEL_ORDER = 0x39;
    public static final byte MODIFY_ORDER = 0x3A;

    // Message type constants - Order response messages (outbound, spec v2.11.90)
    public static final byte ORDER_ACKNOWLEDGMENT   = 0x25;
    public static final byte ORDER_REJECTED         = 0x26;
    public static final byte ORDER_MODIFIED         = 0x27;
    public static final byte ORDER_RESTATED         = 0x28;
    public static final byte USER_MODIFY_REJECTED   = 0x29;
    public static final byte ORDER_CANCELLED        = 0x2A;
    public static final byte CANCEL_REJECTED        = 0x2B;
    public static final byte ORDER_EXECUTION        = 0x2C;
    public static final byte TRADE_CANCEL_CORRECT   = 0x2D;

    public enum Context {
        CLIENT,
        SERVER
    }

    public static BoeProtocolMessage createMessage(BoeMessage message) {
        byte messageType = message.getMessageType();
        Context context = isRequest(messageType) ? Context.SERVER : Context.CLIENT;
        return createMessage(message, context);
    }

    public static BoeProtocolMessage createMessage(BoeMessage message, Context context) {
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
                case REPLAY_COMPLETE -> new ReplayCompleteMessage(data);
                case LOGIN_REQUEST -> parseLoginRequest(data);
                case LOGOUT_REQUEST -> parseLogoutRequest(data);
                case CLIENT_HEARTBEAT -> parseClientHeartbeat(data);

                // Order messages (inbound from client - server receives these)
                case NEW_ORDER    -> NewOrderMessage.parse(data);
                case CANCEL_ORDER -> CancelOrderMessage.parse(data);
                case MODIFY_ORDER -> ModifyOrderMessage.parse(data);

                // Order response messages (outbound to client — server never receives these)
                case ORDER_ACKNOWLEDGMENT -> rejectIfServer(context, "OrderAcknowledgment",
                        () -> OrderAcknowledgmentMessage.fromBytes(data));
                case ORDER_REJECTED       -> rejectIfServer(context, "OrderRejected",
                        () -> OrderRejectedMessage.fromBytes(data));
                case ORDER_MODIFIED       -> rejectIfServer(context, "OrderModified", null);
                case ORDER_RESTATED       -> rejectIfServer(context, "OrderRestated", null);
                case USER_MODIFY_REJECTED -> rejectIfServer(context, "UserModifyRejected", null);
                case ORDER_CANCELLED      -> rejectIfServer(context, "OrderCancelled",
                        () -> OrderCancelledMessage.fromBytes(data));
                case CANCEL_REJECTED      -> rejectIfServer(context, "CancelRejected", null);
                case ORDER_EXECUTION      -> rejectIfServer(context, "OrderExecution",
                        () -> OrderExecutedMessage.fromBytes(data));
                case TRADE_CANCEL_CORRECT -> rejectIfServer(context, "TradeCancelOrCorrect", null);

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

    @FunctionalInterface
    private interface Parser { BoeProtocolMessage parse(); }

    private static BoeProtocolMessage rejectIfServer(Context context, String name, Parser parser) {
        if (context == Context.SERVER) {
            LOGGER.warning("Server received outbound-only message: " + name);
            return null;
        }
        return parser != null ? parser.parse() : null;
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
            case LOGIN_REQUEST    -> "LoginRequest";
            case LOGOUT_REQUEST   -> "LogoutRequest";
            case CLIENT_HEARTBEAT -> "ClientHeartbeat";
            case SERVER_HEARTBEAT -> "ServerHeartbeat";
            case LOGIN_RESPONSE   -> "LoginResponse";
            case LOGOUT_RESPONSE  -> "LogoutResponse";
            case REPLAY_COMPLETE  -> "ReplayComplete";

            // Order messages
            case NEW_ORDER            -> "NewOrder";
            case CANCEL_ORDER         -> "CancelOrder";
            case MODIFY_ORDER         -> "ModifyOrder";
            case ORDER_ACKNOWLEDGMENT -> "OrderAcknowledgment";
            case ORDER_REJECTED       -> "OrderRejected";
            case ORDER_MODIFIED       -> "OrderModified";
            case ORDER_RESTATED       -> "OrderRestated";
            case USER_MODIFY_REJECTED -> "UserModifyRejected";
            case ORDER_CANCELLED      -> "OrderCancelled";
            case CANCEL_REJECTED      -> "CancelRejected";
            case ORDER_EXECUTION      -> "OrderExecution";
            case TRADE_CANCEL_CORRECT -> "TradeCancelOrCorrect";

            default -> "Unknown(0x" + String.format("%02X", messageType) + ")";
        };
    }

    public static boolean isRequest(byte messageType) {
        return messageType == LOGIN_REQUEST ||
                messageType == LOGOUT_REQUEST ||
                messageType == CLIENT_HEARTBEAT ||
                messageType == NEW_ORDER ||
                messageType == CANCEL_ORDER ||
                messageType == MODIFY_ORDER;
    }

    public static boolean isResponse(byte messageType) {
        return messageType == LOGIN_RESPONSE ||
                messageType == LOGOUT_RESPONSE ||
                messageType == SERVER_HEARTBEAT ||
                messageType == REPLAY_COMPLETE ||
                messageType == ORDER_ACKNOWLEDGMENT ||
                messageType == ORDER_REJECTED ||
                messageType == ORDER_MODIFIED ||
                messageType == ORDER_RESTATED ||
                messageType == USER_MODIFY_REJECTED ||
                messageType == ORDER_CANCELLED ||
                messageType == CANCEL_REJECTED ||
                messageType == ORDER_EXECUTION ||
                messageType == TRADE_CANCEL_CORRECT;
    }

    public static boolean isOrderMessage(byte messageType) {
        return messageType == NEW_ORDER ||
                messageType == CANCEL_ORDER ||
                messageType == MODIFY_ORDER ||
                messageType == ORDER_ACKNOWLEDGMENT ||
                messageType == ORDER_REJECTED ||
                messageType == ORDER_MODIFIED ||
                messageType == ORDER_RESTATED ||
                messageType == USER_MODIFY_REJECTED ||
                messageType == ORDER_CANCELLED ||
                messageType == CANCEL_REJECTED ||
                messageType == ORDER_EXECUTION ||
                messageType == TRADE_CANCEL_CORRECT;
    }
}