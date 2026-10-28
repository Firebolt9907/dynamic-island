package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class ExpandedContentMotionTest {
    @Test fun movingEnvelopeKeepsRowsContainedWithoutReservingTheirVacatedSpace() {
        val fullHeight = 220f
        val maxHeight = 78f
        for (step in 0..1000) {
            val p = step / 1000f
            val envelope = ExpandedContentMotion.envelopeHeight(p, fullHeight, maxHeight, 36f)
            assertTrue(envelope <= fullHeight + .001f)
            for (rowHeight in listOf(14f, 40f, 78f)) {
                for (rowTop in listOf(12f, 70f, fullHeight - 12f - rowHeight)) {
                    val center = rowTop + rowHeight / 2f
                    val shift = ExpandedContentMotion.translationYDp(p, center, rowHeight, 36f)
                    assertTrue(rowTop + shift >= 12f - .001f)
                    assertTrue(rowTop + rowHeight + shift <= envelope - 12f + .001f)
                }
            }
        }
        assertTrue(ExpandedContentMotion.envelopeHeight(.5f, fullHeight, maxHeight, 36f) < 190f)
        assertEquals(fullHeight, ExpandedContentMotion.envelopeHeight(1f, fullHeight, maxHeight, 36f), .001f)
    }

    @Test fun verticalTravelIsProportionalToEachElementsDepth() {
        val upper = ExpandedContentMotion.translationYDp(.5f, 50f, 20f, 36f)
        val lower = ExpandedContentMotion.translationYDp(.5f, 150f, 48f, 36f)
        assertTrue(lower < upper)
        assertEquals(upper / (50f - 18f), lower / (150f - 18f), .001f)
        for (center in listOf(50f, 90f, 150f)) {
            for (step in 0..1000) {
                val p = step / 1000f
                val offset = ExpandedContentMotion.translationYDp(p, center, 20f, 36f)
                assertTrue(center - 10f + offset >= 12f - .001f)
                assertTrue(offset <= 0f)
            }
            assertEquals(0f, ExpandedContentMotion.translationYDp(1f, center, 20f, 36f), 0f)
        }
    }

    @Test fun blurReturnsContinuouslyOnCollapseAndClearsAtRest() {
        assertEquals(0f, ExpandedContentMotion.blurRadiusDp(1f), 0f)
        var previous = 0f
        for (step in 1000 downTo 0) {
            val blur = ExpandedContentMotion.blurRadiusDp(step / 1000f)
            assertTrue(blur >= previous)
            assertTrue(blur - previous < .1f)
            previous = blur
        }
        assertEquals(12f, previous, 0f)
    }

    @Test fun everyElementIsHiddenAtRestAndSettledWhenFullyExpanded() {
        for (order in 0..4) {
            assertEquals(0f, ExpandedContentMotion.fraction(0f, order), 0f)
            assertEquals(1f, ExpandedContentMotion.fraction(1f, order), 0f)
            assertEquals(0f, ExpandedContentMotion.fraction(-0.1f, order), 0f)
            assertEquals(1f, ExpandedContentMotion.fraction(1.1f, order), 0f)
        }
    }

    @Test fun titleArtistTimelineControlsAndLyricsArriveInOrder() {
        val amounts = (0..4).map { ExpandedContentMotion.fraction(0.5f, it) }
        amounts.zipWithNext().forEach { (earlier, later) -> assertTrue(earlier > later) }
        assertTrue(amounts.all { it > 0f && it < 1f })
    }

    @Test fun revealIsContinuousAndNeverOvershoots() {
        for (order in 0..4) {
            var previous = 0f
            for (step in 0..100) {
                val amount = ExpandedContentMotion.fraction(step / 100f, order)
                assertTrue(amount in previous..1f)
                assertTrue(amount - previous < 0.04f)
                previous = amount
            }
        }
    }
}
