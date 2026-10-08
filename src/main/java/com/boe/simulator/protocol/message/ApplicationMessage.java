package com.boe.simulator.protocol.message;

public abstract sealed class ApplicationMessage extends BoeProtocolMessage
        permits CancelOrderMessage,
                CancelRejectedMessage,
                MassCancelAcknowledgmentMessage,
                ModifyOrderMessage,
                NewOrderMessage,
                OrderAcknowledgmentMessage,
                OrderCancelledMessage,
                OrderExecutedMessage,
                OrderModifiedMessage,
                OrderRejectedMessage,
                OrderRestatedMessage,
                QuoteUpdateMessage,
                QuoteUpdateRejectedMessage,
                TradeCancelOrCorrectMessage,
                UserModifyRejectedMessage {
}
