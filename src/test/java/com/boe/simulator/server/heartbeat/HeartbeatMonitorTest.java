package com.boe.simulator.server.heartbeat;

import com.boe.simulator.connection.ClientConnectionHandler;
import com.boe.simulator.server.config.ServerConfiguration;
import com.boe.simulator.server.session.ClientSession;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HeartbeatMonitorTest {

    @Mock
    private ClientConnectionHandler mockHandler;
    @Mock
    private ServerConfiguration mockConfig;
    @Mock
    private ClientSession mockSession;
    @Mock
    private ScheduledExecutorService mockScheduler;

    private HeartbeatMonitor heartbeatMonitor;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockHandler.getSession()).thenReturn(mockSession);
        when(mockConfig.getHeartbeatIntervalSeconds()).thenReturn(10L);
        when(mockConfig.getHeartbeatTimeoutSeconds()).thenReturn(30L);
        heartbeatMonitor = new HeartbeatMonitor(mockHandler, mockConfig);
        setField(heartbeatMonitor, "scheduler", mockScheduler);
    }

    @AfterEach
    void tearDown() {
        heartbeatMonitor.shutdown();
    }

    @Test
    void start_shouldScheduleTasks() {
        // Act
        heartbeatMonitor.start();

        // Assert
        assertTrue(heartbeatMonitor.isActive());
        verify(mockScheduler, times(2)).scheduleAtFixedRate(any(Runnable.class), anyLong(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void stop_shouldCancelTasks() {
        // Arrange
        heartbeatMonitor.start();

        // Act
        heartbeatMonitor.stop();

        // Assert
        assertFalse(heartbeatMonitor.isActive());
    }

    @Test
    void shutdown_shouldShutdownScheduler() {
        // Act
        heartbeatMonitor.shutdown();

        // Assert
        verify(mockScheduler).shutdown();
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            fail("Failed to set field '" + fieldName + "': " + e.getMessage());
        }
    }
}