package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Test

class StopwatchTest {
    @Test fun runningUsesMonotonicTimeWithoutAccumulatingTickerError() {
        val clock = Stopwatch(10_000L)
        assertEquals("01:05.43", clock.format(75_430L, true))
        assertEquals("02:00", clock.format(130_000L))
    }
    @Test fun pausedTimeRemainsFixedAndResumePreservesElapsed() {
        val paused = Stopwatch(null, 65_430L)
        assertEquals("01:05.43", paused.format(100_000L, true))
        assertEquals("01:05.43", paused.format(900_000L, true))
        val resumed = Stopwatch(900_000L - paused.pausedElapsedMs)
        assertEquals("01:06.43", resumed.format(901_000L, true))
    }
    @Test fun compactTicksAlignToDisplayedSecondsWithoutBusyPolling() {
        val clock = Stopwatch(10000L)
        assertEquals(1000L, clock.nextCompactTickDelay(10000L))
        assertEquals(570L, clock.nextCompactTickDelay(75430L))
        assertEquals(1L, clock.nextCompactTickDelay(75999L))
        assertEquals(1000L, clock.nextCompactTickDelay(76000L))
        assertEquals(100L, clock.nextCompactTickDelay(9900L))
    }

    @Test fun handlesHoursAndClampsFutureTimeToZero() {
        assertEquals("1:01:01", Stopwatch(0L).format(3_661_000L))
        assertEquals("00:00.00", Stopwatch(5_000L).format(0L, true))
    }
}
