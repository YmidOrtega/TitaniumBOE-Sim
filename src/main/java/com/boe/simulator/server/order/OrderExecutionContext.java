package com.boe.simulator.server.order;


import com.boe.simulator.server.session.ClientSession;

public sealed interface OrderExecutionContext permits RestExecutionContext, TcpExecutionContext {
    String getUsername();
    String getSessionIdentifier();
    boolean supportsNotifications();

    static OrderExecutionContext fromTcpSession(ClientSession session) {
        return new TcpExecutionContext(session);
    }

    static OrderExecutionContext fromRestApi(String username) {
        return new RestExecutionContext(username);
    }
}