package com.island.dynamicisland.ui.island

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SquircleTest {
    @Test fun pillFallsBackToExactStadium() {
        // Radius equal to half the height leaves no smoothing budget: a plain 90° circular arc.
        val k = Squircle.corner(radius = 50f, budget = 50f)
        assertEquals(50f, k.p, 1e-4f)
        assertEquals(90f, k.arcDegrees, 1e-4f)
        assertEquals(50f, k.arcLength, 1e-3f)
        assertEquals(0f, k.a + k.b + k.c + k.d, 1e-3f)
    }

    @Test fun smoothedCornerSegmentsSpanItsEdgeExtent() {
        val k = Squircle.corner(radius = 40f, budget = 200f)
        assertEquals(64f, k.p, 1e-3f) // (1 + 0.6) * r
        assertTrue(k.arcDegrees < 90f)
        // Ease-in, arc and ease-out exactly cover the extent on both axes.
        assertEquals(k.p, k.a + k.b + k.c + k.arcLength + k.d, 1e-3f)
    }

    @Test fun radiusLargerThanBudgetIsClamped() {
        val k = Squircle.corner(radius = 120f, budget = 35f)
        assertEquals(35f, k.radius, 0f)
        assertEquals(35f, k.p, 1e-4f)
    }
}
