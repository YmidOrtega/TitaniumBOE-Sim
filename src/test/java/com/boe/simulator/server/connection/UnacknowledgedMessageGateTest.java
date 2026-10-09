package com.boe.simulator.server.connection;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class UnacknowledgedMessageGateTest {

    @Test
    void onRead_pausesOnlyWhenExceedingThreshold() {
        UnacknowledgedMessageGate gate = new UnacknowledgedMessageGate(1_024, 960);

        for (int i = 0; i < 1_024; i++) assertFalse(gate.onRead());
        assertFalse(gate.isPaused(), "Exactly 1,024 unacknowledged must not pause");

        assertTrue(gate.onRead(), "The 1,025th unacknowledged message pauses reading");
        assertTrue(gate.isPaused());
    }

    @Test
    void onAcknowledged_resumesOnlyBelowResumeThreshold() {
        UnacknowledgedMessageGate gate = new UnacknowledgedMessageGate(1_024, 960);
        for (int i = 0; i < 1_025; i++) gate.onRead();

        for (int i = 0; i < 65; i++) assertFalse(gate.onAcknowledged());
        assertEquals(960, gate.unacknowledged());
        assertTrue(gate.isPaused(), "960 is not below 960");

        assertTrue(gate.onAcknowledged(), "959 unacknowledged resumes reading");
        assertFalse(gate.isPaused());
    }

    @Test
    void awaitReadable_blocksWhilePausedAndReleasesOnResume() throws Exception {
        UnacknowledgedMessageGate gate = new UnacknowledgedMessageGate(2, 1);
        for (int i = 0; i < 3; i++) gate.onRead();

        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try {
                gate.awaitReadable();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThrows(TimeoutException.class, () -> reader.get(200, TimeUnit.MILLISECONDS));

        gate.onAcknowledged();
        gate.onAcknowledged();
        gate.onAcknowledged();

        reader.get(1, TimeUnit.SECONDS);
    }

    @Test
    void close_releasesPausedReader() throws Exception {
        UnacknowledgedMessageGate gate = new UnacknowledgedMessageGate(1, 1);
        gate.onRead();
        gate.onRead();

        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            try {
                gate.awaitReadable();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        assertThrows(TimeoutException.class, () -> reader.get(200, TimeUnit.MILLISECONDS));
        gate.close();

        assertDoesNotThrow(() -> reader.get(1, TimeUnit.SECONDS), "close releases the paused reader");
    }

    @Test
    void constructor_rejectsResumeAbovePause() {
        assertThrows(IllegalArgumentException.class, () -> new UnacknowledgedMessageGate(10, 11));
        assertThrows(IllegalArgumentException.class, () -> new UnacknowledgedMessageGate(10, 0));
    }
}
