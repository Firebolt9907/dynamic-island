package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class MusicContentContainmentTest {
    @Test fun visibleArtworkAndBarsShrinkMonotonicallyToTheirCompactSizes() {
        var previousArt = Float.MAX_VALUE
        var previousBars = Float.MAX_VALUE
        for (step in 1000 downTo 0) {
            val p = step / 1000f
            val width = 160f + 240f * IslandGrowth.widthFraction(p)
            val height = 36f + 166f * IslandGrowth.heightFraction(p)
            val scale = IslandContentBounds.scale(width, height, 18f + 26f * p,
                400f, ExpandedContentMotion.envelopeHeight(p, 202f, 78f, 36f), 12f - 3f * ExpandedContentMotion.blurRadiusDp(p))
            val art = MusicElementMorph.artworkSlotSize(21.6f, 72f, scale, p) * (1f - 20f * p / 72f)
            val bars = MusicElementMorph.sharedSize(19.44f, 24f, scale, p)
            assertTrue("Artwork grew during collapse", art <= previousArt + .001f)
            assertTrue("Visualizer grew during collapse", bars <= previousBars + .001f)
            previousArt = art
            previousBars = bars
        }
        assertEquals(21.6f, previousArt, .001f)
        assertEquals(19.44f, previousBars, .001f)
    }

    @Test fun visibleEdgesAndVerticalCentersMatchThroughoutBothDirections() {
        for (header in listOf(56f, 72f, 96f)) {
            val fullHeight = header + 130f
            for (step in (0..1000) + (1000 downTo 0)) {
                val p = step / 1000f
                val width = 160f + 240f * IslandGrowth.widthFraction(p)
                val height = 36f + (fullHeight - 36f) * IslandGrowth.heightFraction(p)
                val safe = IslandContentBounds.inset(width, height, 18f + 26f * p)
                val envelope = ExpandedContentMotion.envelopeHeight(p, fullHeight, maxOf(header, 78f), 36f)
                val scale = IslandContentBounds.scale(width, height, 18f + 26f * p,
                    400f, envelope, 12f - 3f * ExpandedContentMotion.blurRadiusDp(p))
                val art = MusicElementMorph.artworkSlotSize(21.6f, header, scale, p)
                val inset = art * p * (10f / header)
                val barsWidth = MusicElementMorph.sharedSize(19.44f, 24f, scale, p)
                val barsHeight = MusicElementMorph.sharedSize(15.12f, 20f, scale, p)
                val endMargin = (width - 400f * scale) / 2f + 22f * scale
                val endCenter = (height - envelope * scale) / 2f + (12f + header / 2f) * scale
                val frame = MusicElementMorph.symmetricPlacement(width, height, safe,
                    7.2f + (endMargin - 7.2f) * p, 18f + (endCenter - 18f) * p,
                    art, inset, barsWidth, barsHeight)
                assertEquals(frame.artLeft + inset, width - frame.barsLeft - barsWidth, .001f)
                assertEquals(frame.artTop + art / 2f, frame.barsTop + barsHeight / 2f, .001f)
                assertTrue(frame.artLeft >= safe - .001f)
                assertTrue(frame.artTop >= safe - .001f)
                assertTrue(frame.barsLeft + barsWidth <= width - safe + .001f)
                assertTrue(frame.artTop + art <= height - safe + .001f)
            }
        }
    }

    @Test fun artworkAndEqualizerFitThroughoutOpeningAndClosing() {
        for (header in listOf(56f, 72f, 96f)) {
            val fullHeight = header + 130f
            for (step in (0..1000) + (1000 downTo 0)) {
                val p = step / 1000f
                val width = 160f + 240f * IslandGrowth.widthFraction(p)
                val height = 36f + (fullHeight - 36f) * IslandGrowth.heightFraction(p)
                val radius = 18f + 26f * p
                val inset = IslandContentBounds.inset(width, height, radius)
                val envelope = ExpandedContentMotion.envelopeHeight(p, fullHeight, maxOf(header, 78f), 36f)
                val scale = IslandContentBounds.scale(width, height, radius, 400f, envelope,
                    12f - 3f * ExpandedContentMotion.blurRadiusDp(p))
                val art = MusicElementMorph.sharedSize(21.6f, header, scale, p)
                val barsWidth = MusicElementMorph.sharedSize(19.44f, 24f, scale, p)
                val barsHeight = MusicElementMorph.sharedSize(15.12f, 20f, scale, p)
                assertTrue(art <= height - 2f * inset)
                assertTrue(barsWidth <= width - 2f * inset)
                assertTrue(barsHeight <= height - 2f * inset)
                if (step == 1000) {
                    assertEquals(header * scale, art, .001f)
                    assertEquals(24f * scale, barsWidth, .001f)
                    assertEquals(20f * scale, barsHeight, .001f)
                }
            }
        }
    }
}
