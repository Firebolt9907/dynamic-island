package com.island.dynamicisland.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsMatchingTest {
    private val track = LyricsTrack("I Wish You Would (Taylor's Version)", "Taylor Swift", "1989 (Taylor's Version) [Deluxe]", 207751)

    @Test fun acceptsTheSameRecordingAcrossStandardAndDeluxeLabels() {
        assertTrue(LyricsRepository.matchesLineRecording(track, "I Wish You Would (Taylor’s version)", "Taylor Swift", "1989 (Taylor’s Version)", 208000))
        assertTrue(LyricsRepository.matchesLineRecording(track, track.title, track.artist, "1989 (Taylor's Version) (Deluxe Edition)", 208000))
    }
    @Test fun doesNotBorrowLyricsFromTheOriginalLiveKaraokeOrOtherAlbum() {
        assertFalse(LyricsRepository.matchesLineRecording(track, "I Wish You Would", track.artist, "1989", 207000))
        assertFalse(LyricsRepository.matchesLineRecording(track, track.title, track.artist, "Taylor Swift Karaoke: 1989 (Taylor's Version)", 208000))
        assertFalse(LyricsRepository.matchesLineRecording(track, track.title + " (Live)", track.artist, track.album, 208000))
        assertFalse(LyricsRepository.matchesLineRecording(track, track.title, track.artist, "Unrelated Album", 208000))
        assertFalse(LyricsRepository.matchesLineRecording(track, track.title, track.artist, track.album, 219000))
    }
}
