package com.boe.simulator.protocol.message;

public abstract sealed class BoeProtocolMessage
        permits CancelOrderMessage, CancelRejectedMessage,
                ClientHeartbeatMessage,
                LoginRequestMessage, LoginResponseMessage,
                LogoutRequestMessage, LogoutResponseMessage,
                NewOrderMessage, OrderAcknowledgmentMessage,
                OrderCancelledMessage, OrderExecutedMessage,
                OrderModifiedMessage, OrderRejectedMessage,
                OrderRestatedMessage, ReplayCompleteMessage,
                ServerHeartbeatMessage, TradeCancelOrCorrectMessage,
                UserModifyRejectedMessage {

    public abstract byte[] toBytes();

    public abstract byte getMessageType();
}
