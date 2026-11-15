package com.island.dynamicisland.lyrics

import org.junit.Assert.*
import org.junit.Test

class LyricsParserTest {
    @Test fun parsesClockAndOffsetTimesAndRejectsInvalidValues() {
        assertEquals(3723456L, LyricsParser.timeMs("1:02:03.456"))
        assertEquals(1250L, LyricsParser.timeMs("1.25s"))
        assertEquals(1250L, LyricsParser.timeMs("1250ms"))
        assertNull(LyricsParser.timeMs("NaN"))
        assertNull(LyricsParser.timeMs("-1s"))
        assertNull(LyricsParser.timeMs("invalid"))
    }

    @Test fun lrcHandlesRepeatedTimestampsAndSilentGaps() {
        val lyrics = LyricsParser.lrc("[00:01.00][00:06.00]First line\n[00:03.00]\n[00:09.00]Last line", 12000)!!
        assertEquals(listOf(1000L, 6000L, 9000L), lyrics.lines.map { it.startMs })
        assertEquals(0, lyrics.lineAt(1000))
        assertEquals(-1, lyrics.lineAt(3000))
        assertEquals(-1, lyrics.lineAt(500))
        assertEquals(1, lyrics.lineAt(6100))
        assertEquals(-1, lyrics.lineAt(12000))
        assertFalse(lyrics.wordSynced)
    }

    @Test fun lrcDoesNotExtendLinesPastTheRecording() {
        val lyrics = LyricsParser.lrc("[00:01.123]First\n[00:20.00]Outside recording", 5000)!!
        assertEquals(listOf(LyricLine(1123, 5000, "First")), lyrics.lines)
        assertEquals(-1, lyrics.lineAt(5000))
    }

    @Test fun extremeNegativeOffsetCannotWrapIntoTheStartOfTheRecording() {
        assertNull(LyricsParser.lrc("[offset:-9223372036854775808]\n[00:01.00]Late", 5000))
    }

    @Test fun enhancedLrcKeepsRealWordBoundariesAndSpaces() {
        val lyrics = LyricsParser.lrc("[offset:250]\n[00:01.00]<00:01.00>One <00:02.00>two", 4000)!!
        val line = lyrics.lines.single()
        assertEquals("One two", line.text)
        assertEquals(750L, line.startMs)
        assertEquals(LyricSegment(750, 1750, 0, 4), line.segments[0])
        assertEquals(LyricSegment(1750, 4000, 4, 7), line.segments[1])
        assertTrue(lyrics.wordSynced)
    }

    @Test fun ttmlKeepsSyllablesAndSkipsBackgroundAndTranslationText() {
        val raw = """<tt xmlns="http://www.w3.org/ns/ttml" xmlns:ttm="http://www.w3.org/ns/ttml#metadata"><body dur="10s"><div>
          <p begin="1s" end="4s"><span><span begin="1s" end="2s">Hel</span><span begin="2s" end="3s">lo</span></span> <span begin="3s" end="4s">world</span><span ttm:role="x-bg"><span begin="2s" end="3s">background</span></span><span ttm:role="x-translation">translation</span></p>
          <p begin="6s" end="8s">A plain line</p>
        </div></body></tt>"""
        val lyrics = LyricsParser.ttml(raw)!!
        assertEquals("Hello world", lyrics.lines[0].text)
        assertEquals(listOf("Hel", "lo", "world"), lyrics.lines[0].segments.map { lyrics.lines[0].text.substring(it.from, it.until) })
        assertEquals(10000L, lyrics.durationMs)
        assertEquals(-1, lyrics.lineAt(4500))
        assertEquals(1, lyrics.lineAt(6000))
        assertTrue(lyrics.wordSynced)
    }

    @Test fun malformedAndUntimedDocumentsDoNotProduceFakeSync() {
        assertNull(LyricsParser.ttml("<tt><p>Missing timing</p></tt>"))
        assertNull(LyricsParser.ttml("<tt><body>"))
        assertNull(LyricsParser.ttml("<!DOCTYPE tt [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><tt/>"))
        assertNull(LyricsParser.lrc("Unsynced plain text", 10000))
    }

    @Test fun lineOnlyTtmlRemainsLineSynced() {
        val lyrics = LyricsParser.ttml("<tt><body><p begin='1s' end='2s'>One line</p></body></tt>")!!
        assertFalse(lyrics.wordSynced)
        assertEquals(listOf(1000L, 2000L), lyrics.boundaries)
    }

    @Test fun ttmlMayFinishBeforeAnInstrumentalOutroButCannotOverrunTheRecording() {
        val lyrics = SyncedLyrics(listOf(LyricLine(1000, 174954, "Final vocal")), "AMLL TTML DB", 174954)
        assertTrue(LyricsRepository.fitsRecording(lyrics, 178426))
        assertFalse(LyricsRepository.fitsRecording(lyrics, 160000))
    }

    @Test fun recordingKeysIncludeAlbumAndDuration() {
        val track = LyricsTrack("Song", "Artist", "Album", 120000)
        assertNotEquals(track.key, track.copy(album = "Live Album").key)
        assertNotEquals(track.key, track.copy(durationMs = 180000).key)
        assertEquals(track.key, track.copy().key)
    }
}
