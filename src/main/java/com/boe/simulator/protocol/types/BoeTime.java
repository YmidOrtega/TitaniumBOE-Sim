package com.boe.simulator.protocol.types;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Wire encodings for the BOE temporal data types (spec v2.11.90, Data Types).
 *
 *   DateTime — 8 bytes, nanoseconds past the UNIX epoch (UTC)
 *   Date     — 4 bytes, YYYYMMDD expressed as an integer (e.g. 20110319)
 */
public final class BoeTime {

    // Option maturity dates are U.S. exchange calendar dates
    private static final ZoneId EXCHANGE_ZONE = ZoneId.of("America/New_York");

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

    public static int toYyyymmdd(Instant instant) {
        if (instant == null) return 0;
        LocalDate d = instant.atZone(EXCHANGE_ZONE).toLocalDate();
        return d.getYear() * 10000 + d.getMonthValue() * 100 + d.getDayOfMonth();
    }

    public static Instant fromYyyymmdd(int yyyymmdd) {
        if (yyyymmdd == 0) return null;
        int y = yyyymmdd / 10000, m = (yyyymmdd / 100) % 100, d = yyyymmdd % 100;
        return LocalDate.of(y, m, d).atStartOfDay(EXCHANGE_ZONE).toInstant();
    }
}
