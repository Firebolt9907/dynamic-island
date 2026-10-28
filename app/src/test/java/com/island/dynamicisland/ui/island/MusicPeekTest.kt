package com.island.dynamicisland.ui.island

import com.island.dynamicisland.media.NowPlaying
import org.junit.Assert.*
import org.junit.Test

class MusicPeekTest {
    private fun track(id: String = "one") = NowPlaying("Song $id", "Artist", null, true,
        0, 180_000, "music.app", 0, mediaId = id)

    @Test fun onlyActualSongChangesTriggerPeek() {
        val changes = MusicTrackChangeTracker()
        val first = track()
        assertFalse(changes.changed(first))
        assertFalse(changes.changed(first.copy(positionMs = 5_000)))
        assertFalse(changes.changed(first.copy(isPlaying = false)))
        assertFalse(changes.changed(first.copy(title = "Corrected title")))
        assertTrue(changes.changed(track("two")))
        assertFalse(changes.changed(null))
        assertFalse(changes.changed(track("three")))
    }

    @Test fun fallbackIdentityAndLabelIncludeArtist() {
        val changes = MusicTrackChangeTracker()
        val first = track().copy(mediaId = null)
        changes.changed(first)
        assertTrue(changes.changed(first.copy(artist = "Other artist")))
        assertEquals("Song one · Artist", musicPeekLabel(first))
        assertEquals("Song one", musicPeekLabel(first.copy(artist = "")))
    }

    @Test fun longerTitlesHaveTimeToScroll() {
        assertEquals(4_500L, musicPeekDuration(100f, 260f))
        assertEquals(12_600L, musicPeekDuration(610f, 260f))
        assertEquals(30_000L, musicPeekDuration(5_000f, 260f))
    }
}
