package com.island.dynamicisland.ui.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ControlConfirmationMotionTest {
    @Test fun initialAndRepeatedStateDoNotBounceAndChangesSettleToIdle() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = ControlConfirmationMotion(true)
        scope.launch { motion.update(true, animate = true) }
        runCurrent()
        assertEquals(0, frames.pendingRequests)
        assertEquals(0f, motion.emphasis.value, 0f)
        scope.launch { motion.update(false, animate = true) }
        frames.step(this, 1)
        repeat(5) { frames.step(this, 16) }
        assertTrue(motion.emphasis.value > .1f)
        repeat(150) { frames.step(this, 16) }
        assertEquals(0f, motion.emphasis.value, .001f)
        assertFalse(motion.emphasis.isRunning)
        assertEquals(0, frames.pendingRequests)
    }

    @Test fun changesConsumedWhileHiddenDoNotReplayWhenTheCardOpens() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = ControlConfirmationMotion(false)
        scope.launch { motion.update(true, animate = false) }
        runCurrent()
        scope.launch { motion.update(true, animate = true) }
        runCurrent()
        assertEquals(0f, motion.emphasis.value, 0f)
        assertEquals(0, frames.pendingRequests)
    }
}
