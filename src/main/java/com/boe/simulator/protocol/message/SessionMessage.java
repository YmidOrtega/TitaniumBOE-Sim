package com.boe.simulator.protocol.message;

public abstract sealed class SessionMessage extends BoeProtocolMessage
        permits ClientHeartbeatMessage,
                LoginRequestMessage,
                LoginResponseMessage,
                LogoutRequestMessage,
                LogoutResponseMessage,
                ReplayCompleteMessage,
                ServerHeartbeatMessage {
}
