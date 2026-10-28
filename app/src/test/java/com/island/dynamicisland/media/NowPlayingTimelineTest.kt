package com.island.dynamicisland.media

import org.junit.Assert.*
import org.junit.Test

class NowPlayingTimelineTest {
    private fun track() = NowPlaying(
        title = "Track", artist = "Artist", albumArt = null, isPlaying = true,
        positionMs = 10000, durationMs = 200000, packageName = "test.player", accentColor = 0,
        mediaId = "one", playbackSpeed = 1f, publishedAtElapsedMs = 1000,
    )

    @Test fun openingAfterLongCompactOrHiddenPeriodUsesCurrentTime() {
        val snapshot = track()
        assertEquals(70000L, snapshot.positionAt(61000))
        assertEquals(130000L, snapshot.copy(playbackSpeed = 2f).positionAt(61000))
        assertEquals(10000L, snapshot.positionMs)
    }

    @Test fun pauseSeekAndSpeedCallbacksReplaceTheTimeline() {
        val snapshot = track()
        assertEquals(10000L, snapshot.copy(isPlaying = false).positionAt(61000))
        assertEquals(91000L, snapshot.copy(positionMs = 90000, publishedAtElapsedMs = 60000).positionAt(61000))
        assertEquals(9000L, snapshot.copy(playbackSpeed = -1f).positionAt(2000))
    }

    @Test fun invalidOrStationaryTimelinesDoNotScheduleUpdates() {
        val snapshot = track()
        listOf(snapshot.copy(isPlaying = false), snapshot.copy(playbackSpeed = 0f),
            snapshot.copy(playbackSpeed = Float.NaN), snapshot.copy(publishedAtElapsedMs = 0),
            snapshot.copy(durationMs = 0)).forEach { assertFalse(it.advancesAt(2000)) }
        assertEquals(10000L, snapshot.copy(publishedAtElapsedMs = 0).positionAt(61000))
    }

    @Test fun reachingEitherEndpointStopsUpdatesUntilASessionCallback() {
        val snapshot = track()
        assertTrue(snapshot.advancesAt(2000))
        assertEquals(200000L, snapshot.positionAt(500000))
        assertFalse(snapshot.advancesAt(500000))
        val reverse = snapshot.copy(playbackSpeed = -1f)
        assertTrue(reverse.advancesAt(2000))
        assertEquals(0L, reverse.positionAt(20000))
        assertFalse(reverse.advancesAt(20000))
    }
}
