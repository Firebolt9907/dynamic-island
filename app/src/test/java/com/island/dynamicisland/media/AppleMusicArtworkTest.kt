package com.island.dynamicisland.media

import org.junit.Assert.*
import org.junit.Test

class AppleMusicArtworkTest {
    @Test fun albumKeyIgnoresTrackAndDownloadMetadata() {
        assertEquals(
            AppleMusicArtwork.cacheKey("First Song", "Artist", "Déjà Vu"),
            AppleMusicArtwork.cacheKey("Second Song (Downloaded)", "ARTIST", "Deja Vu"),
        )
        assertEquals(
            AppleMusicArtwork.cacheKey("First", "Artist feat. Guest", "Album", "Artist"),
            AppleMusicArtwork.cacheKey("Second", "Artist", "Album", "Artist"),
        )
        assertNotEquals(AppleMusicArtwork.cacheKey("Song", "A", "Album"), AppleMusicArtwork.cacheKey("Song", "B", "Album"))
        assertNotEquals(AppleMusicArtwork.cacheKey("First", "A", ""), AppleMusicArtwork.cacheKey("Second", "A", ""))
    }

    @Test fun albumMatchingDoesNotDependOnTrackTitleOrDuration() {
        assertTrue(AppleMusicArtwork.matchesAlbum("Déjà Vu", "Beyoncé", "Deja Vu", "Beyonce"))
        assertFalse(AppleMusicArtwork.matchesAlbum("Album", "Artist", "Album Deluxe", "Artist"))
        assertFalse(AppleMusicArtwork.matchesAlbum("Album", "Artist", "Album", "Other Artist"))
    }

    @Test fun matchesOnlyTheSameRecording() {
        assertTrue(AppleMusicArtwork.matches("Déjà Vu", "Beyoncé", "Album", 180000, "Deja Vu", "Beyonce", "Album", 181000))
        assertFalse(AppleMusicArtwork.matches("Song", "Artist", "Album", 180000, "Song (Live)", "Artist", "Album", 180000))
        assertFalse(AppleMusicArtwork.matches("Song", "Artist", "Album", 180000, "Song", "Cover Band", "Album", 180000))
        assertFalse(AppleMusicArtwork.matches("Song", "Artist", "Album", 180000, "Song", "Artist", "Other Album", 180000))
        assertFalse(AppleMusicArtwork.matches("Song", "Artist", "", 180000, "Song", "Artist", "Album", 230000))
    }

    private fun page(header: String, recommendation: String = "{}") = """
        <script type="application/json" id="serialized-server-data">
        {"data":[{"data":{"sections":[{"items":[$header]},{"items":[$recommendation]}]}}]}
        </script>
    """.trimIndent()

    private fun motion(url: String) = """{"videoArtwork":{"dictionary":{"motionDetailSquare":{"video":"$url"}}}}"""

    @Test fun extractsSquareCoverMotionFromAlbumHeader() {
        val url = "https://mvod.itunes.apple.com/itunes-assets/HLSMusic/video.m3u8"
        assertEquals(url, AppleMusicArtwork.motionUrl(page(motion(url))))
    }

    @Test fun missingMotionDoesNotUseRecommendationsOrTallVideo() {
        val video = motion("https://mvod.itunes.apple.com/other.m3u8")
        assertNull(AppleMusicArtwork.motionUrl(page("{}", video)))
        assertNull(AppleMusicArtwork.motionUrl(page(video.replace("videoArtwork", "tallVideoArtwork"))))
        assertNull(AppleMusicArtwork.motionUrl("<html>Unavailable</html>"))
    }

    @Test fun rejectsNonAppleAndInsecureMotionUrls() {
        assertNull(AppleMusicArtwork.motionUrl(page(motion("https://example.com/video.m3u8"))))
        assertNull(AppleMusicArtwork.motionUrl(page(motion("http://mvod.itunes.apple.com/video.m3u8"))))
    }
}
