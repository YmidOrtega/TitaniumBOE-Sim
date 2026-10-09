package com.boe.simulator.protocol.types;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
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
    void toYyyymmdd_shouldMatchSpecExampleBytes() {
        // Spec example: EF DB 32 01 = MaturityDate 2011-03-19
        Instant maturity = LocalDate.of(2011, 3, 19).atStartOfDay(ZoneId.of("America/New_York")).toInstant();

        byte[] wire = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(BoeTime.toYyyymmdd(maturity)).array();

        assertEquals(20110319, BoeTime.toYyyymmdd(maturity));
        assertArrayEquals(new byte[]{(byte) 0xEF, (byte) 0xDB, 0x32, 0x01}, wire);
    }

    @Test
    void fromYyyymmdd_shouldRoundTrip() {
        Instant maturity = BoeTime.fromYyyymmdd(20110319);

        assertEquals(20110319, BoeTime.toYyyymmdd(maturity));
    }

    @Test
    void zeroValues_shouldMapToNull() {
        assertNull(BoeTime.fromYyyymmdd(0));
        assertNull(BoeTime.fromEpochNanos(0L));
        assertEquals(0, BoeTime.toYyyymmdd(null));
        assertEquals(0L, BoeTime.toEpochNanos(null));
    }
}
