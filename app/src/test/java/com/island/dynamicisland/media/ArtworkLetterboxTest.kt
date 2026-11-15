package com.island.dynamicisland.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtworkLetterboxTest {
    private val black = 0xFF000000.toInt()
    private val red = 0xFFD03030.toInt()

    private fun image(w: Int, h: Int, fill: (Int, Int) -> Int) = IntArray(w * h) { fill(it % w, it / w) }

    @Test
    fun trimsVideoLetterboxBars() {
        // 16:9 content centered in a 160x160 square: 45px bars top and bottom.
        val px = image(160, 160) { _, y -> if (y in 45 until 115) red else black }
        assertEquals(ArtworkLetterbox.Bounds(0, 45, 160, 115), ArtworkLetterbox.contentBounds(px, 160, 160))
    }

    @Test
    fun trimsTransparentPillarbox() {
        val px = image(100, 100) { x, _ -> if (x in 20 until 80) red else 0 }
        assertEquals(ArtworkLetterbox.Bounds(20, 0, 80, 100), ArtworkLetterbox.contentBounds(px, 100, 100))
    }

    @Test
    fun leavesNormalCoversAndThinBordersAlone() {
        assertNull(ArtworkLetterbox.contentBounds(image(100, 100) { _, _ -> red }, 100, 100))
        assertNull(ArtworkLetterbox.contentBounds(image(100, 100) { _, y -> if (y < 2) black else red }, 100, 100))
    }

    @Test
    fun leavesMostlyDarkArtworkAlone() {
        assertNull(ArtworkLetterbox.contentBounds(image(100, 100) { _, _ -> black }, 100, 100))
        assertNull(ArtworkLetterbox.contentBounds(image(100, 100) { _, y -> if (y in 45 until 55) red else black }, 100, 100))
    }
}
