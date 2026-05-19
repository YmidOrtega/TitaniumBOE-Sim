package com.boe.simulator.server.heartbeat;

import com.boe.simulator.server.connection.ClientConnectionHandler;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.session.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeartbeatMonitorTest {

    @Mock
    private ClientConnectionHandler mockHandler;
    @Mock
    private ServerConfiguration mockConfig;
    @Mock
    private ClientSession mockSession;

    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockHandler.getSession()).thenReturn(mockSession);
        when(mockSession.getConnectionId()).thenReturn(1);
        when(mockConfig.getHeartbeatIntervalSeconds()).thenReturn(10L);
        when(mockConfig.getHeartbeatTimeoutSeconds()).thenReturn(30L);
        heartbeatMonitor = new HeartbeatMonitor(mockHandler, mockConfig);
    }

    @AfterEach
    void tearDown() {
        heartbeatMonitor.shutdown();
    }

    @Test
    void start_shouldBecomeActive() {
        heartbeatMonitor.start();

        assertTrue(heartbeatMonitor.isActive());
    }

    @Test
    void start_twice_shouldBeIdempotent() {
        heartbeatMonitor.start();
        heartbeatMonitor.start();

        assertTrue(heartbeatMonitor.isActive());
    }

    @Test
    void stop_shouldDeactivate() {
        heartbeatMonitor.start();

        heartbeatMonitor.stop();

        assertFalse(heartbeatMonitor.isActive());
    }

    @Test
    void shutdown_shouldDeactivate() {
        heartbeatMonitor.start();

        heartbeatMonitor.shutdown();

        assertFalse(heartbeatMonitor.isActive());
    }

    @Test
    void isActive_beforeStart_shouldBeFalse() {
        assertFalse(heartbeatMonitor.isActive());
    }
}
