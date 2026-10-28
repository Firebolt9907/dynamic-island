package com.island.dynamicisland.ui.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import com.island.dynamicisland.lyrics.LyricLine
import com.island.dynamicisland.lyrics.SyncedLyrics
import org.junit.Test

class LyricTimingTest {
    @Test fun lineScrollUsesItsLifetimeAndLeavesTimeToReadBothEnds() {
        assertEquals(0f, lyricLineScrollProgress(1100, 1000, 3000), .001f)
        assertEquals(.5f, lyricLineScrollProgress(2000, 1000, 3000), .001f)
        assertEquals(1f, lyricLineScrollProgress(2800, 1000, 3000), .001f)
        assertTrue(lyricLineScrollProgress(6000, 1000, 21000) < .3f)
        assertEquals(1f, lyricLineScrollProgress(20700, 1000, 21000), .001f)
        assertTrue(lyricLineScrollProgress(1500, 1000, 1800) > .5f)
    }

    @Test fun shortInstrumentalGapsRetainTheRowButLongGapsDoNot() {
        val lyrics = SyncedLyrics(listOf(
            LyricLine(1000, 3000, "Sung line"),
            LyricLine(3000, 4500, "♪"),
            LyricLine(4500, 6000, "Next line"),
            LyricLine(10000, 11000, "Later line"),
        ), "test")
        assertEquals(LyricGap(0, 1000), shortLyricGapAt(lyrics, 500, 12000))
        assertEquals(LyricGap(3000, 4500), shortLyricGapAt(lyrics, 3500, 12000))
        assertNull(shortLyricGapAt(lyrics, 2000, 12000))
        assertNull(shortLyricGapAt(lyrics, 8000, 12000))
        assertEquals(LyricGap(11000, 12000), shortLyricGapAt(lyrics, 11500, 12000))
    }

    @Test fun adjacentPlaceholdersDoNotFlashTheRowDuringALongInstrumental() {
        val lyrics = SyncedLyrics(listOf(
            LyricLine(0, 1000, "Sung line"),
            LyricLine(1000, 2000, "♪"),
            LyricLine(2000, 3000, "♫"),
            LyricLine(5000, 6000, "Next line"),
        ), "test")
        assertNull(shortLyricGapAt(lyrics, 1500, 7000))
        assertNull(shortLyricGapAt(lyrics, 3500, 7000))
    }

    @Test fun instrumentalPlaceholdersDoNotReserveLyricSpace() {
        assertFalse(hasVisibleLyrics(" ♪ ♫ 🎵🎶 "))
        assertFalse(hasVisibleLyrics("  \n"))
        assertTrue(hasVisibleLyrics("♪ A sung line ♪"))
        assertTrue(hasVisibleLyrics("歌詞"))
    }

    @Test fun wordFillTracksPlaybackIncludingSeeksAndReversal() {
        assertEquals(0f, lyricWordProgress(500, 1000, 2000), .001f)
        assertEquals(.5f, lyricWordProgress(1500, 1000, 2000), .001f)
        assertEquals(1f, lyricWordProgress(2500, 1000, 2000), .001f)
        assertEquals(.25f, lyricWordProgress(1250, 1000, 2000), .001f)
    }
    @Test fun zeroLengthSegmentsCannotProduceInvalidFillValues() {
        assertEquals(0f, lyricWordProgress(500, 1000, 1000), .001f)
        assertEquals(1f, lyricWordProgress(1000, 1000, 1000), .001f)
    }
    @Test fun schedulesRealBoundariesAtPlaybackSpeed() {
        val boundaries = listOf(1000L, 1500L, 2300L)
        assertEquals(500L, lyricTickDelay(boundaries, 1000, 1f))
        assertEquals(250L, lyricTickDelay(boundaries, 1000, 2f))
        assertEquals(500L, lyricTickDelay(boundaries, 2000, -1f))
        assertEquals(16L, lyricTickDelay(boundaries, 1500, -1f))
        assertEquals(1000L, lyricTickDelay(boundaries, 5000, 1f))
        assertEquals(1000L, lyricTickDelay(boundaries, 1000, 0f))
    }
}
