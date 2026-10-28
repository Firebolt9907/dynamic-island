package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class ArtworkFlipMotionTest {
    @Test fun exchangesArtworkOnlyAtTheEdgeOnMidpoint() {
        assertFalse(ArtworkFlipMotion.showsNewArtwork(.499f))
        assertTrue(ArtworkFlipMotion.showsNewArtwork(.5f))
        assertEquals(-89.82f, ArtworkFlipMotion.rotationY(.499f), .001f)
        assertEquals(90f, ArtworkFlipMotion.rotationY(.5f), 0f)
    }
    @Test fun endpointsAreFlatAndNeitherFaceIsEverMirrored() {
        assertEquals(0f, ArtworkFlipMotion.rotationY(0f), 0f)
        assertEquals(0f, ArtworkFlipMotion.rotationY(1f), 0f)
        for (step in -10..110) assertTrue(kotlin.math.abs(ArtworkFlipMotion.rotationY(step / 100f)) <= 90f)
    }
}
