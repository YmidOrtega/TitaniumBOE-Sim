package com.boe.simulator.protocol.message;

public abstract sealed class ApplicationMessage extends BoeProtocolMessage
        permits CancelOrderMessage,
                CancelRejectedMessage,
                NewOrderMessage,
                OrderAcknowledgmentMessage,
                OrderCancelledMessage,
                OrderExecutedMessage,
                OrderModifiedMessage,
                OrderRejectedMessage,
                OrderRestatedMessage,
                TradeCancelOrCorrectMessage,
                UserModifyRejectedMessage {
}
