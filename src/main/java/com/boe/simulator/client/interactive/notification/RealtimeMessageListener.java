package com.boe.simulator.client.interactive.notification;

import com.boe.simulator.client.listener.BoeMessageListener;
import com.boe.simulator.protocol.message.*;

public class RealtimeMessageListener implements BoeMessageListener {

    private final NotificationManager notificationManager;

    public RealtimeMessageListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public void onOrderAcknowledgment(OrderAcknowledgmentMessage ack) {
        String message = String.format("Order %s accepted (ID: %d)",
                ack.getClOrdID(), ack.getOrderID());
        notificationManager.notify(NotificationManager.NotificationType.ORDER_ACCEPTED, message);
    }

    public void onOrderExecuted(OrderExecutedMessage executed) {
        String message = String.format("Order %s executed: %d @ %s",
                executed.getClOrdID(),
                executed.getLastShares(),
                executed.getLastPx());
        notificationManager.notify(NotificationManager.NotificationType.ORDER_EXECUTED, message);
    }

    public void onOrderCancelled(OrderCancelledMessage cancelled) {
        String message = String.format("Order %s cancelled", cancelled.getClOrdID());
        notificationManager.notify(NotificationManager.NotificationType.ORDER_CANCELLED, message);
    }

    public void onOrderRejected(OrderRejectedMessage rejected) {
        String message = String.format("Order %s rejected: %s",
                rejected.getClOrdID(),
                rejected.getOrderRejectReason());
        notificationManager.notify(NotificationManager.NotificationType.ORDER_REJECTED, message);
    }

    @Override
    public void onLoginResponse(LoginResponseMessage response) {
        if (response.isAccepted()) notificationManager.notify(NotificationManager.NotificationType.INFO, "Login successful");
        else notificationManager.notify(NotificationManager.NotificationType.ERROR, "Login failed: " + response.getLoginResponseText());
    }

    @Override
    public void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
    }

    @Override
    public void onLogoutResponse(LogoutResponseMessage response) {
        notificationManager.notify(NotificationManager.NotificationType.INFO, "Logout completed");
    }
}