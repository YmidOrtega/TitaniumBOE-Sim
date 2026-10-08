package com.boe.simulator.protocol.types;

import java.time.Instant;

/**
 * Wire encodings for the BOE temporal data types (spec v2.11.90, Data Types).
 *
 *   DateTime — 8 bytes, nanoseconds past the UNIX epoch (UTC)
 */
public final class BoeTime {

    private BoeTime() {}

    // System.nanoTime() is monotonic with an arbitrary origin, so it cannot be used here
    public static long nowEpochNanos() {
        return toEpochNanos(Instant.now());
    }

    public static long toEpochNanos(Instant instant) {
        if (instant == null) return 0L;
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), 1_000_000_000L), instant.getNano());
    }

    public static Instant fromEpochNanos(long epochNanos) {
        if (epochNanos == 0L) return null;
        return Instant.ofEpochSecond(0L, epochNanos);
    }
}
