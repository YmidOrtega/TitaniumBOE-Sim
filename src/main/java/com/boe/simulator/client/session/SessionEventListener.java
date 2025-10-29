package com.boe.simulator.client.session;

import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;

public interface SessionEventListener {

    default void onConnected(String host, int port) {
    }

    default void onDisconnected(String reason) {
    }

    default void onLoginSuccess(LoginResponseMessage response) {
    }

    default void onLoginFailed(LoginResponseMessage response) {
    }

    default void onLogoutCompleted(LogoutResponseMessage response) {
    }

    default void onStateChanged(ClientSessionState oldState, ClientSessionState newState) {
    }

    default void onError(String context, Throwable error) {
    }

    default void onReconnecting(int attemptNumber) {
    }
}
