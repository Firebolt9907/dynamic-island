package com.island.dynamicisland.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelDelayTest {
    @Test fun showsTheCaptureThatIsDueAfterTheDelay() {
        val delay = LevelDelay(2)
        delay.push(1_000, floatArrayOf(.1f, .1f))
        delay.push(1_050, floatArrayOf(.9f, .2f))
        delay.push(1_100, floatArrayOf(.3f, .3f))
        val out = FloatArray(2)
        // At 1 120 ms with 65 ms of delay, the listener hears audio captured at 1 055: the 1 050 capture.
        assertTrue(delay.read(1_120 - 65, out))
        assertArrayEquals(floatArrayOf(.9f, .2f), out, 0f)
    }

    @Test fun holdsWhenNothingIsDueYet() {
        val delay = LevelDelay(1)
        delay.push(1_000, floatArrayOf(.5f))
        val out = floatArrayOf(.7f)
        assertFalse(delay.read(999, out))
        assertArrayEquals(floatArrayOf(.7f), out, 0f)
    }

    @Test fun keepsOnlyTheMostRecentCapturesAndClears() {
        val delay = LevelDelay(1, capacity = 3)
        for (t in 0 until 5) delay.push(t * 50L, floatArrayOf(t.toFloat()))
        val out = FloatArray(1)
        assertTrue(delay.read(10_000, out)); assertArrayEquals(floatArrayOf(4f), out, 0f)
        assertFalse("capture at 50 ms was overwritten", delay.read(60, out))
        delay.clear()
        assertFalse(delay.read(10_000, out))
    }
}
