package com.boe.simulator.server.order;

import com.boe.simulator.server.session.ClientSession;

public class TcpExecutionContext implements OrderExecutionContext {
    private final ClientSession session;

    public TcpExecutionContext(ClientSession session) {
        this.session = session;
    }

    @Override
    public String getUsername() {
        return session.getUsername();
    }

    @Override
    public String getSessionIdentifier() {
        return "TCP-" + session.getConnectionId();
    }

    @Override
    public boolean supportsNotifications() {
        return true;
    }

    public ClientSession getSession() {
        return session;
    }
}