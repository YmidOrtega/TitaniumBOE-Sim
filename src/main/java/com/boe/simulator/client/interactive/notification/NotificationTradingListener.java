package com.boe.simulator.client.interactive.notification;

import com.boe.simulator.client.listener.TradingMessageListener;
import com.boe.simulator.protocol.message.*;

public class NotificationTradingListener implements TradingMessageListener {

    private final NotificationManager notificationManager;

    public NotificationTradingListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    @Override
    public void onOrderAcknowledgment(OrderAcknowledgmentMessage message) {
        String msg = String.format("Order %s acknowledged (ID: %d)",
                message.getClOrdID(),
                message.getOrderID());

        notificationManager.notify(
                NotificationManager.NotificationType.ORDER_ACCEPTED,
                msg
        );
    }

    @Override
    public void onOrderExecuted(OrderExecutedMessage message) {
        String msg;
        if (message.isFilled()) {
            msg = String.format("Filled %s: %d @ %.2f (Exec ID: %d)",
                    message.getSymbol() != null ? message.getSymbol() : message.getClOrdID(),
                    message.getLastShares(),
                    message.getLastPx().doubleValue(),
                    message.getExecID());
        } else {
            msg = String.format("Partial fill %s: %d @ %.2f, %d remaining (Exec ID: %d)",
                    message.getSymbol() != null ? message.getSymbol() : message.getClOrdID(),
                    message.getLastShares(),
                    message.getLastPx().doubleValue(),
                    message.getLeavesQty(),
                    message.getExecID());
        }

        notificationManager.notify(
                NotificationManager.NotificationType.ORDER_EXECUTED,
                msg
        );
    }

    @Override
    public void onOrderRejected(OrderRejectedMessage message) {
        String msg = String.format("Order %s rejected: %s",
                message.getClOrdID(),
                message.getText());

        notificationManager.notify(
                NotificationManager.NotificationType.ORDER_REJECTED,
                msg
        );
    }

    @Override
    public void onOrderCancelled(OrderCancelledMessage message) {
        String msg = String.format("Order %s cancelled",
                message.getClOrdID());

        notificationManager.notify(
                NotificationManager.NotificationType.ORDER_CANCELLED,
                msg
        );
    }
}