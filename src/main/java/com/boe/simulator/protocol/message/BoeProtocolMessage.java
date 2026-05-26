package com.boe.simulator.protocol.message;

public abstract sealed class BoeProtocolMessage
        permits CancelOrderMessage, ClientHeartbeatMessage,
                LoginRequestMessage, LoginResponseMessage,
                LogoutRequestMessage, LogoutResponseMessage,
                NewOrderMessage, OrderAcknowledgmentMessage,
                OrderCancelledMessage, OrderExecutedMessage,
                OrderRejectedMessage, ReplayCompleteMessage,
                ServerHeartbeatMessage {

    public abstract byte[] toBytes();

    public abstract byte getMessageType();
}
