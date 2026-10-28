package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandGrowthTest {
    @Test fun silhouetteWidensAheadOfHeightAlongAMonotonicCurve() {
        var lastWidth = -1f
        var lastHeight = -1f
        for (step in 0..100) {
            val p = step / 100f
            val width = IslandGrowth.widthFraction(p)
            val height = IslandGrowth.heightFraction(p)
            assertTrue(width > lastWidth && height > lastHeight)
            assertTrue(width in p..1f && height in 0f..p)
            lastWidth = width
            lastHeight = height
        }
        assertEquals(0f, IslandGrowth.widthFraction(0f), 0f)
        assertEquals(0f, IslandGrowth.heightFraction(0f), 0f)
        assertEquals(1f, lastWidth, 0f)
        assertEquals(1f, lastHeight, 0f)
    }

    @Test fun tertiaryVisualizerDoesNotDetourAsItsCardMovesBackToTheRight() {
        val compact = 340f
        val expanded = 365f
        var previous = expanded + 1f
        for (step in 100 downTo 0) {
            val p = step / 100f
            val width = 40f + 360f * IslandGrowth.widthFraction(p)
            val cardLeft = 200f + 140f * (1f - p) - width / 2f
            val local = MusicElementMorph.travellingElementLeft(compact, expanded, cardLeft, p)
            val screen = cardLeft + local
            assertTrue(screen <= previous)
            assertTrue(screen in compact..expanded)
            previous = screen
        }
    }

    @Test fun sharedIconsBowDownwardWithoutMovingTheirEndpoints() {
        assertEquals(0f, MusicElementMorph.downwardArcDp(0f), 0f)
        assertEquals(8f, MusicElementMorph.downwardArcDp(.5f), 0f)
        assertEquals(0f, MusicElementMorph.downwardArcDp(1f), 0f)
        assertEquals(MusicElementMorph.downwardArcDp(.25f), MusicElementMorph.downwardArcDp(.75f), 0f)
    }

    @Test fun growthIsContinuousMonotonicAndEasedWithoutIntermediateStops() {
        val duration = IslandGrowth.durationForHold(500L)
        val spec = IslandGrowth.spec(duration).vectorize(Float.VectorConverter)
        val initial = AnimationVector1D(0f)
        val target = AnimationVector1D(1f)
        val velocity = AnimationVector1D(0f)
        var previous = -1f
        for (ms in 0..duration step 25) {
            val value = spec.getValueFromNanos(ms * 1_000_000L, initial, target, velocity).value
            assertTrue("No intermediate plateau at ${ms}ms", value > previous)
            assertTrue(value in 0f..1f)
            previous = value
        }
        val early = spec.getVelocityFromNanos(1_000_000, initial, target, velocity).value
        val accelerating = spec.getVelocityFromNanos(65_000_000, initial, target, velocity).value
        val late = spec.getVelocityFromNanos(699_000_000, initial, target, velocity).value
        assertTrue(accelerating > early * 5f)
        assertTrue(accelerating > late * 5f)
    }

    @Test fun restartingFromAnIntermediateSizeHasNoValueJumpAndStillReachesFullSize() {
        val from = 0.25f
        val spec = IslandGrowth.spec(800).vectorize(Float.VectorConverter)
        val initial = AnimationVector1D(from)
        val target = AnimationVector1D(1f)
        val velocity = AnimationVector1D(0f)
        assertEquals(from, spec.getValueFromNanos(0L, initial, target, velocity).value, 0f)
        assertEquals(1f, spec.getValueFromNanos(spec.getDurationNanos(initial, target, velocity), initial, target, velocity).value, 0.002f)
    }

    @Test fun growthContinuesBeyondCustomLongPressTimeouts() {
        for (timeout in listOf(400L, 500L, 1000L, 1500L)) {
            assertTrue(IslandGrowth.durationForHold(timeout) > timeout)
        }
        assertTrue(IslandGrowth.hapticProgress > 0.5f && IslandGrowth.hapticProgress < 1f)
    }
}
