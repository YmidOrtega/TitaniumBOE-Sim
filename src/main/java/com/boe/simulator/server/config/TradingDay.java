package com.boe.simulator.server.config;

import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** The trading day ends at 17:30 America/New_York, when Cboe sends Logout E (p.47). */
public final class TradingDay {
    public static final ZoneId MARKET_ZONE = ZoneId.of("America/New_York");
    public static final LocalTime MARKET_CLOSE = LocalTime.of(17, 30);

    private TradingDay() {}

    public static Instant lastClose(Instant now) {
        ZonedDateTime local = now.atZone(MARKET_ZONE);
        ZonedDateTime close = local.with(MARKET_CLOSE);
        if (close.isAfter(local)) close = local.toLocalDate().minusDays(1).atTime(MARKET_CLOSE).atZone(MARKET_ZONE);
        return close.toInstant();
    }
}
