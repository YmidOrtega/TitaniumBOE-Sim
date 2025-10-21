package com.boe.simulator.protocol.message;

public enum SessionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    AUTHENTICATING,
    AUTHENTICATED,
    ACTIVE,
    HEARTBEAT_TIMEOUT,
    DISCONNECTING,
    ERROR
}
