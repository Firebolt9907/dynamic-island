package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.min

class IslandContentBoundsTest {
    @Test fun contentFitsRoundedInteriorAtEveryFrameInBothDirections() {
        for (fullHeight in listOf(150f, 220f, 400f)) {
            for (step in (0..1000) + (1000 downTo 0)) {
                val p = step / 1000f
                val width = 160f + (400f - 160f) * IslandGrowth.widthFraction(p)
                val height = 36f + (fullHeight - 36f) * IslandGrowth.heightFraction(p)
                val radius = min(18f + (44f - 18f) * p, min(width, height) / 2f)
                val padding = 12f - 3f * ExpandedContentMotion.blurRadiusDp(p)
                val scale = IslandContentBounds.scale(width, height, radius, 400f, fullHeight, padding)
                val x = (width - (400f - 2f * padding) * scale) / 2f
                val y = (height - (fullHeight - 2f * padding) * scale) / 2f
                assertTrue(scale in 0f..1f)
                assertInsideCorner(x, y, radius)
                assertTrue(x >= 0f && y >= 0f)
            }
        }
    }

    @Test fun sharedElementsStayInsideCornerEvenWhenDesiredPositionIsOutside() {
        for (step in 0..1000) {
            val p = step / 1000f
            val w = 160f + 240f * IslandGrowth.widthFraction(p)
            val h = 36f + 184f * IslandGrowth.heightFraction(p)
            val r = min(18f + 26f * p, min(w, h) / 2f)
            val inset = IslandContentBounds.inset(w, h, r)
            val size = 21.6f + (56f - 21.6f) * p
            for (desired in listOf(-100f, 0f, 1000f)) {
                val x = IslandContentBounds.position(desired, w, size, inset)
                val y = IslandContentBounds.position(desired, h, size, inset)
                assertTrue(x + size <= w - inset + .001f)
                assertTrue(y + size <= h - inset + .001f)
                assertInsideCorner(x, y, r)
            }
        }
    }

    private fun assertInsideCorner(x: Float, y: Float, radius: Float) {
        val dx = (radius - x).coerceAtLeast(0f)
        val dy = (radius - y).coerceAtLeast(0f)
        assertTrue("Content crossed rounded edge", dx * dx + dy * dy <= radius * radius + .01f)
    }
}
