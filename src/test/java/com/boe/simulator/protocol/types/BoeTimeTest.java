package com.boe.simulator.protocol.types;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

class BoeTimeTest {

    @Test
    void toEpochNanos_shouldMatchSpecExample() {
        // Spec: 1,294,909,373,757,324,000 = 2011-01-13 09:02:53.757324 UTC
        Instant instant = Instant.parse("2011-01-13T09:02:53.757324Z");

        assertEquals(1_294_909_373_757_324_000L, BoeTime.toEpochNanos(instant));
    }

    @Test
    void fromEpochNanos_shouldRoundTripSpecExample() {
        Instant instant = BoeTime.fromEpochNanos(1_294_909_373_757_324_000L);

        assertEquals(Instant.parse("2011-01-13T09:02:53.757324Z"), instant);
    }

    @Test
    void nowEpochNanos_shouldBeCloseToWallClock() {
        long before = BoeTime.toEpochNanos(Instant.now());
        long now = BoeTime.nowEpochNanos();
        long after = BoeTime.toEpochNanos(Instant.now());

        assertTrue(now >= before && now <= after, "DateTime must be wall-clock nanos past the UNIX epoch");
    }

    @Test
    void zeroValues_shouldMapToNull() {
        assertNull(BoeTime.fromEpochNanos(0L));
        assertEquals(0L, BoeTime.toEpochNanos(null));
    }
}
