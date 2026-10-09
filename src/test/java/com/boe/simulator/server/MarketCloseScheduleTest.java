package com.boe.simulator.server;

import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class MarketCloseScheduleTest {

    private static final ZoneId NEW_YORK = ZoneId.of("America/New_York");

    private static long hoursUntilClose(String newYorkDateTime) {
        ZonedDateTime now = ZonedDateTime.of(java.time.LocalDateTime.parse(newYorkDateTime), NEW_YORK);
        return CboeServer.secondsUntilNextClose(now);
    }

    @Test
    void beforeTheClose_isTheSameDay() {
        assertEquals(5 * 3600 + 30 * 60, hoursUntilClose("2026-10-08T12:00:00"));
    }

    @Test
    void afterTheClose_isTheNextDay() {
        assertEquals(23 * 3600 + 30 * 60, hoursUntilClose("2026-10-08T18:00:00"));
    }

    @Test
    void exactlyAtTheClose_isTheNextDay() {
        assertEquals(24 * 3600, hoursUntilClose("2026-10-08T17:30:00"));
    }

    @Test
    void daylightSavingEnds_theNextCloseIsStill1730Local() {
        // 2026-11-01: clocks go back one hour in New York, so 18:00 -> next 17:30 is 24.5 h, not 23.5 h
        assertEquals(24 * 3600 + 30 * 60, hoursUntilClose("2026-10-31T18:00:00"));
    }

    @Test
    void sameInstantFromAnotherZone_givesTheSameDelay() {
        ZonedDateTime utc = ZonedDateTime.of(java.time.LocalDateTime.parse("2026-10-08T16:00:00"), ZoneId.of("UTC"));
        assertEquals(5 * 3600 + 30 * 60, CboeServer.secondsUntilNextClose(utc), "16:00 UTC is 12:00 in New York in October");
    }
}
