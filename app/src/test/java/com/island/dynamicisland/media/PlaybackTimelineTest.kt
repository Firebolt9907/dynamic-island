package com.island.dynamicisland.media

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackTimelineTest {
    @Test fun playbackAdvancesUsingTheSessionClockAndSpeed() {
        assertEquals(6000L, PlaybackTimeline.position(5000, 1000, 1500, 20000, true, 2f))
    }

    @Test fun pausedAndUnknownPositionsDoNotDrift() {
        assertEquals(5000L, PlaybackTimeline.position(5000, 1000, 9000, 20000, false, 1f))
        assertEquals(0L, PlaybackTimeline.position(-1, 1000, 9000, 20000, true, 1f))
    }

    @Test fun invalidClocksAndSpeedsKeepTheReportedPosition() {
        assertEquals(5000L, PlaybackTimeline.position(5000, 0, 9000, 20000, true, 1f))
        assertEquals(5000L, PlaybackTimeline.position(5000, 1000, 900, 20000, true, 1f))
        assertEquals(5000L, PlaybackTimeline.position(5000, 1000, 9000, 20000, true, Float.NaN))
        assertEquals(5000L, PlaybackTimeline.position(5000, 1000, 9000, 20000, true, Float.POSITIVE_INFINITY))
    }

    @Test fun forwardAndReversePositionsStayWithinTrackBounds() {
        assertEquals(20000L, PlaybackTimeline.position(19900, 1000, 1500, 20000, true, 1f))
        assertEquals(0L, PlaybackTimeline.position(100, 1000, 1500, 20000, true, -1f))
        assertEquals(6000L, PlaybackTimeline.position(5000, 1000, 2000, 0, true, 1f))
    }
}
