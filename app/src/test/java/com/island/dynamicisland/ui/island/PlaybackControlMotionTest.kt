package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class PlaybackControlMotionTest {
    @Test fun toggleBounceCanOvershootWithoutBringingBackTheOldIcon() {
        val frame = PlaybackControlMotion.toggle(.72f)
        assertTrue(frame.scale > 1f)
        assertEquals(1f, frame.blend, 0f)
    }

    @Test fun toggleCrossfadesOnlyBelowFortyPercentAndShrinksToTwentyPercent() {
        assertEquals(ToggleFrame(1f, 0f), PlaybackControlMotion.toggle(0f))
        assertEquals(ToggleFrame(1f, 1f), PlaybackControlMotion.toggle(1f))
        assertEquals(.2f, PlaybackControlMotion.toggle(.38f).scale, .0001f)
        assertEquals(.5f, PlaybackControlMotion.toggle(.38f).blend, .0001f)
        for (step in 0..1000) {
            val frame = PlaybackControlMotion.toggle(step / 1000f)
            assertTrue(frame.scale >= .1999f && frame.scale <= 1.1201f)
            if (frame.blend > 0f && frame.blend < 1f) assertTrue(frame.scale < .4f)
        }
    }

    @Test fun toggleHasContinuousVelocityAtShrinkAndBounceTurns() {
        val h = .0001f
        for (turn in listOf(.38f, .72f, .86f)) {
            val before = PlaybackControlMotion.toggle(turn - h).scale
            val at = PlaybackControlMotion.toggle(turn).scale
            val after = PlaybackControlMotion.toggle(turn + h).scale
            assertEquals(0f, (at - before) / h, .03f)
            assertEquals(0f, (after - at) / h, .03f)
        }
    }

    @Test fun skipStartsAndEndsWithTheSameTwoVisibleSlots() {
        for (previous in listOf(false, true)) {
            fun visible(p: Float) = PlaybackControlMotion.triangles(p, previous)
                .filter { it.alpha > 0f && it.scale > 0f }.map { it.left }.sorted()
            assertEquals(listOf(9f, 24f), visible(0f))
            assertEquals(visible(0f), visible(1f))
        }
    }
    @Test fun previousExactlyMirrorsNextThroughTheWholeTransition() {
        for (step in 0..100) {
            val p = step / 100f
            val next = PlaybackControlMotion.triangles(p, false)
            val previous = PlaybackControlMotion.triangles(p, true)
            next.zip(previous).forEach { (a, b) ->
                assertEquals(48f, a.left + b.left + 15f, .0001f)
                assertEquals(a.scale, b.scale, 0f)
                assertEquals(a.alpha, b.alpha, 0f)
            }
            assertEquals(1f, next[1].alpha, 0f)
            assertEquals(p, next[0].scale, 0f)
            assertEquals(1f - p, next[2].alpha, 0f)
        }
    }
}
