package com.island.dynamicisland.ui.island

import org.junit.Assert.assertEquals
import org.junit.Test

class MusicElementMorphTest {
    @Test fun promotedVisualizerStartsAtSecondaryRatherThanMergedPillRightEdge() {
        val height = 40f
        val secondaryWidth = compactMusicPillWidth(height)
        val density = 3f
        val compactLeft = (8f + height * (CompactArtworkLeadingInset + CompactArtworkTrailingGap + CompactArtworkFraction) + 6f) * density
        assertEquals(compactLeft,
            MusicElementMorph.promotedBarsLeft(secondaryWidth, height, 900f, 0f, density), .001f)
        assertEquals(900f,
            MusicElementMorph.promotedBarsLeft(secondaryWidth, height, 900f, 1f, density), .001f)
        assertEquals((compactLeft + 900f) / 2f,
            MusicElementMorph.promotedBarsLeft(secondaryWidth, height, 900f, .5f, density), .001f)
    }

    @Test fun endpointsMatchCompactAndExpandedArtworkSlots() {
        assertPosition(7.4f, 7.4f, MusicElementMorph.art(37f, 52f, 0f))
        assertPosition(12f, 52f, MusicElementMorph.art(37f, 52f, 1f))
    }

    @Test fun endpointsMatchCompactAndExpandedVisualizerSlots() {
        assertPosition((168f - 37f * (.2f + CompactVisualizerTrailingInset + CompactVisualizerWidthFraction)), (37f * (1f - CompactVisualizerHeightFraction) / 2f), MusicElementMorph.visualizer(168f, 37f, 387f, 52f, 0f))
        assertPosition(351f, 69f, MusicElementMorph.visualizer(168f, 37f, 387f, 52f, 1f))
    }

    @Test fun elementsFollowTheSameContinuousPathInEitherDirection() {
        for (step in 0..100) {
            val p = step / 100f
            assertPosition(7.4f + (12f - 7.4f) * p, 7.4f + (52f - 7.4f) * p,
                MusicElementMorph.art(37f, 52f, p))
            assertPosition((168f - 37f * (.2f + CompactVisualizerTrailingInset + CompactVisualizerWidthFraction)) + (351f - (168f - 37f * (.2f + CompactVisualizerTrailingInset + CompactVisualizerWidthFraction))) * p, (37f * (1f - CompactVisualizerHeightFraction) / 2f) + (69f - (37f * (1f - CompactVisualizerHeightFraction) / 2f)) * p,
                MusicElementMorph.visualizer(168f, 37f, 387f, 52f, p))
        }
    }

    @Test fun enlargedHeaderKeepsArtworkAndBarsCenteredThroughoutTheMorph() {
        for (headerHeight in listOf(54f, 76f, 108f)) {
            for (step in 0..100) {
                val p = step / 100f
                val art = MusicElementMorph.art(37f, 52f, p, headerHeight)
                val bars = MusicElementMorph.visualizer(168f, 37f, 387f, 52f, p, headerHeight)
                val artSize = 37f * 0.60f + (54f - 37f * 0.60f) * p
                val barsHeight = 37f * CompactVisualizerHeightFraction + (20f - 37f * CompactVisualizerHeightFraction) * p
                assertEquals(art.top + artSize / 2f, bars.top + barsHeight / 2f, 0.0001f)
            }
            val art = MusicElementMorph.art(37f, 52f, 1f, headerHeight)
            val bars = MusicElementMorph.visualizer(168f, 37f, 387f, 52f, 1f, headerHeight)
            assertEquals(52f + headerHeight / 2f, art.top + 27f, 0.0001f)
            assertEquals(52f + headerHeight / 2f, bars.top + 10f, 0.0001f)
        }
    }

    private fun assertPosition(left: Float, top: Float, actual: MusicElementPosition) {
        assertEquals(left, actual.left, 0.0001f)
        assertEquals(top, actual.top, 0.0001f)
    }
}
