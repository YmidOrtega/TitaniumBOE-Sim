package com.boe.simulator.api.websocket;

import org.eclipse.jetty.websocket.api.Session;
import org.junit.jupiter.api.Test;

import io.javalin.websocket.WsContext;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketServiceCleanupTest {

    private static Session openSession() {
        Session session = mock(Session.class);
        when(session.isOpen()).thenReturn(true);
        return session;
    }

    @Test
    void cleanup_closesAndRemovesIdleOpenSessions() throws Exception {
        WebSocketService service = new WebSocketService();
        Session jetty = openSession();
        service.addSession("idle", new WsContext("idle", jetty) {}, null);
        Thread.sleep(20);

        assertEquals(1, service.cleanupInactiveSessions(10));

        verify(jetty).close(1001, "Idle timeout");
        assertEquals(0, service.getActiveSessionCount());
    }

    @Test
    void cleanup_keepsSessionsWithRecentActivity() throws Exception {
        WebSocketService service = new WebSocketService();
        Session jetty = openSession();
        service.addSession("live", new WsContext("live", jetty) {}, null);
        Thread.sleep(20);
        service.recordActivity("live");

        assertEquals(0, service.cleanupInactiveSessions(10));

        verify(jetty, never()).close(anyInt(), anyString());
        assertEquals(1, service.getActiveSessionCount());
    }

    @Test
    void cleanup_removesSessionsWhoseSocketIsAlreadyClosed() {
        WebSocketService service = new WebSocketService();
        Session jetty = mock(Session.class);
        when(jetty.isOpen()).thenReturn(false);
        service.addSession("gone", new WsContext("gone", jetty) {}, null);

        assertEquals(1, service.cleanupInactiveSessions(60_000));

        verify(jetty, never()).close(anyInt(), anyString());
        assertEquals(0, service.getActiveSessionCount());
    }
}
