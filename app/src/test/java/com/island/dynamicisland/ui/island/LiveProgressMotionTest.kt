package com.island.dynamicisland.ui.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveProgressMotionTest {
    @Test fun onePercentUpdatePassesThroughIntermediateValues() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = LiveProgressMotion(.42f)
        scope.launch { motion.update(.43f, animate = true) }
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        assertTrue(motion.progress.value > .42f && motion.progress.value < .429f)
        repeat(150) { frames.step(this, 16) }
        assertEquals(.43f, motion.progress.value, .0001f)
        assertFalse(motion.progress.isRunning)
    }

    @Test fun rapidProgressCorrectionRetainsTheVisiblePositionAndSettles() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = LiveProgressMotion(.2f)
        val first = scope.launch { motion.update(.9f, animate = true) }
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        val visible = motion.progress.value
        assertTrue(visible > .2f && visible < .9f)
        first.cancel()
        runCurrent()
        scope.launch { motion.update(.1f, animate = true) }
        runCurrent()
        assertEquals(visible, motion.progress.value, .0001f)
        repeat(150) {
            frames.step(this, 16)
            assertTrue(motion.progress.value in 0f..1f)
        }
        assertEquals(.1f, motion.progress.value, .001f)
        assertFalse(motion.progress.isRunning)
        assertEquals(0, frames.pendingRequests)
    }

    @Test fun hiddenOrReducedMotionUpdatesNeedNoAnimationFrames() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = LiveProgressMotion(.2f)
        scope.launch { motion.update(.85f, animate = false) }
        runCurrent()
        assertEquals(.85f, motion.progress.value, 0f)
        assertEquals(0, frames.pendingRequests)
        assertFalse(motion.progress.isRunning)
    }

    @Test fun malformedAndOutOfRangeProgressCannotEscapeItsTrack() = runTest {
        val motion = LiveProgressMotion(Float.NaN)
        assertEquals(0f, motion.progress.value, 0f)
        motion.update(2f, animate = false)
        assertEquals(1f, motion.progress.value, 0f)
        motion.update(-.5f, animate = false)
        assertEquals(0f, motion.progress.value, 0f)
        motion.update(Float.POSITIVE_INFINITY, animate = false)
        assertEquals(0f, motion.progress.value, 0f)
    }
}
