package com.boe.simulator.server.session;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

import com.boe.simulator.server.session.BoeSessionState.InboundCheck;

class BoeSessionStateTest {

    private static byte[] encode(int seq) {
        return new byte[]{(byte) seq};
    }

    @Test
    void sendSequenced_numbersFromOneAndJournalsEveryMessage() throws IOException {
        BoeSessionState state = new BoeSessionState("U1", "S1");
        ByteArrayOutputStream wire = new ByteArrayOutputStream();

        state.sendSequenced(BoeSessionStateTest::encode, wire::write);
        state.sendSequenced(BoeSessionStateTest::encode, wire::write);
        state.sendSequenced(BoeSessionStateTest::encode, null);

        assertArrayEquals(new byte[]{1, 2}, wire.toByteArray());
        assertEquals(3, state.lastSentSequence());
        assertEquals(3, state.messagesAfter(0).size());
    }

    @Test
    void sendSequenced_journalsEvenWhenTheWriteFails() {
        BoeSessionState state = new BoeSessionState("U1", "S1");

        assertThrows(IOException.class, () -> state.sendSequenced(BoeSessionStateTest::encode, b -> {
            throw new IOException("socket closed");
        }));

        assertEquals(1, state.lastSentSequence());
        assertArrayEquals(new byte[]{1}, state.messagesAfter(0).get(0));
    }

    @Test
    void messagesAfter_returnsOnlyTheGap() throws IOException {
        BoeSessionState state = new BoeSessionState("U1", "S1");
        for (int i = 0; i < 5; i++) state.sendSequenced(BoeSessionStateTest::encode, null);

        List<byte[]> missed = state.messagesAfter(3);

        assertEquals(2, missed.size());
        assertArrayEquals(new byte[]{4}, missed.get(0));
        assertArrayEquals(new byte[]{5}, missed.get(1));
        assertTrue(state.messagesAfter(5).isEmpty());
    }

    @Test
    void checkInbound_acceptsForwardGapsAndUnsequenced() {
        BoeSessionState state = new BoeSessionState("U1", "S1");

        assertEquals(InboundCheck.ACCEPTED, state.checkInbound(1));
        assertEquals(InboundCheck.ACCEPTED, state.checkInbound(10), "Gap forward is ignored");
        assertEquals(InboundCheck.UNSEQUENCED, state.checkInbound(0), "Member may send 0");
        assertEquals(10, state.lastProcessedInbound());
    }

    @Test
    void checkInbound_rejectsBackwardAndRepeatedSequence() {
        BoeSessionState state = new BoeSessionState("U1", "S1");
        state.checkInbound(5);

        assertEquals(InboundCheck.BACKWARD, state.checkInbound(5), "Same sequence twice is a gap backward");
        assertEquals(InboundCheck.BACKWARD, state.checkInbound(4));
        assertEquals(5, state.lastProcessedInbound());
    }

    @Test
    void registry_keepsStatePerUsernameAndSubIdAcrossBinds() throws IOException {
        BoeSessionRegistry registry = new BoeSessionRegistry();
        BoeSessionState first = registry.bind("U1", "S1");
        first.sendSequenced(BoeSessionStateTest::encode, null);

        assertSame(first, registry.bind("U1", "S1"), "Reconnecting to the same session keeps its sequence");
        assertNotSame(first, registry.bind("U1", "S2"));
        assertEquals("S2", registry.latestForUser("U1").getSessionSubID());
    }
}
