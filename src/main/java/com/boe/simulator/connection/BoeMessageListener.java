package com.boe.simulator.connection;

import com.boe.simulator.protocol.message.LoginResponseMessage;
import com.boe.simulator.protocol.message.LogoutResponseMessage;
import com.boe.simulator.protocol.message.ServerHeartbeatMessage;
import com.boe.simulator.protocol.message.BoeMessage;


public interface BoeMessageListener {
    

    default void onLoginResponse(LoginResponseMessage response) {
    }

    default void onLogoutResponse(LogoutResponseMessage response) {
    }
    
    default void onServerHeartbeat(ServerHeartbeatMessage heartbeat) {
    }
    
    default void onUnknownMessage(BoeMessage message) {
    }
    
    default void onMessageError(byte messageType, Exception error) {
    }
}