package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class PlaybackControlMotionTest {
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
