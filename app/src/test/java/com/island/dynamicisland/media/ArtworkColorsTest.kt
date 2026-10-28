package com.island.dynamicisland.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkColorsTest {
    @Test fun opaqueArtworkPreservesItsColor() {
        assertEquals(0xFF40A0E0.toInt(), ArtworkColors.fromPixels(intArrayOf(0xFF40A0E0.toInt(), 0xFF40A0E0.toInt())))
    }
    @Test fun transparentPixelsUseTheDisplayedBackgroundInsteadOfHiddenRgb() {
        assertEquals(0xFF1C1C1F.toInt(), ArtworkColors.fromPixels(intArrayOf(0x00FF0000)))
        assertEquals(0xFF8D0D0F.toInt(), ArtworkColors.fromPixels(intArrayOf(0x80FF0000.toInt())))
    }
    @Test fun sampleAveragesTheArtworkAndHandlesMissingPixels() {
        assertEquals(0xFF7F007F.toInt(), ArtworkColors.fromPixels(intArrayOf(0xFFFF0000.toInt(), 0xFF0000FF.toInt())))
        assertEquals(ArtworkColors.FALLBACK, ArtworkColors.fromPixels(intArrayOf()))
    }
}
