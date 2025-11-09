package com.boe.simulator.client.listener;

import com.boe.simulator.protocol.message.*;

public interface TradingMessageListener {

    // Called when the server acknowledges an order
    void onOrderAcknowledgment(OrderAcknowledgmentMessage message);

    // Called when an order is executed (filled)
    void onOrderExecuted(OrderExecutedMessage message);

    // Called when an order is rejected
    void onOrderRejected(OrderRejectedMessage message);

    // Called when an order is cancelled
    void onOrderCancelled(OrderCancelledMessage message);
}