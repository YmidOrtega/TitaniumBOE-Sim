package com.boe.simulator.client.interactive.notification;

import com.boe.simulator.client.session.ClientSessionState;
import com.boe.simulator.client.session.SessionEventListener;
import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;

public class NotificationSessionListener implements SessionEventListener {

    private final NotificationManager notificationManager;

    public NotificationSessionListener(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    @Override
    public void onConnected(String host, int port) {
    }

    @Override
    public void onDisconnected(String reason) {
        notificationManager.notify(NotificationManager.NotificationType.WARNING, "Disconnected: " + reason);
    }

    @Override
    public void onLoginSuccess(LoginResponseMessage response) {
        notificationManager.notify(NotificationManager.NotificationType.INFO, "Login successful");
    }

    @Override
    public void onLoginFailed(LoginResponseMessage response) {
        notificationManager.notify(NotificationManager.NotificationType.ERROR, "Login failed: " + response.getLoginResponseText());
    }

    @Override
    public void onLogoutCompleted(LogoutResponseMessage response) {
        notificationManager.notify(NotificationManager.NotificationType.INFO, "Logout completed");
    }

    @Override
    public void onStateChanged(ClientSessionState oldState, ClientSessionState newState) {
    }

    @Override
    public void onReconnecting(int attempt) {
        notificationManager.notify(NotificationManager.NotificationType.WARNING, "Reconnecting... (attempt " + attempt + ")");
    }

    public void onError(String errorType, Exception error) {
        notificationManager.notify(NotificationManager.NotificationType.ERROR, "Error: " + error.getMessage());
    }
}