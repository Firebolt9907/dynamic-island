package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Test

class CountdownTest {
    @Test fun pausedTimerKeepsRemainingTimeUntilReplacedOnResume() {
        val clock = Countdown(0L, pausedRemainingMs = 65_001L)
        assertEquals("01:06", clock.format(10_000L))
        assertEquals("01:06", clock.format(900_000L))
        assertEquals("01:05", Countdown(965_001L).format(901_000L))
        assertEquals("00:00", Countdown(0L, pausedRemainingMs = -1L).format(0L))
    }
    @Test fun ticksWithoutNotificationReposts() {
        val clock = Countdown(310_000L)
        assertEquals("05:00", clock.format(10_000L))
        assertEquals("04:59", clock.format(11_000L))
        assertEquals("04:01", clock.format(69_000L))
    }

    @Test fun roundsUpAndStopsAtZero() {
        val clock = Countdown(10_000L)
        assertEquals("00:01", clock.format(9_999L))
        assertEquals("00:00", clock.format(10_000L))
        assertEquals("00:00", clock.format(11_000L))
        assertEquals(1L, clock.nextTickDelay(9_999L))
        assertEquals(1000L, clock.nextTickDelay(9_000L))
    }

    @Test fun supportsHourBoundary() {
        val clock = Countdown(3_600_000L)
        assertEquals("1:00:00", clock.format(0L))
        assertEquals("59:59", clock.format(1000L))
    }
}
