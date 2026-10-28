package com.island.dynamicisland.ui.island

import com.island.dynamicisland.power.allowInteractionMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicFeedbackMotionTest {
    @Test fun everyThermalStateStillProducesIntermediateArtworkFrames() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = MusicFeedbackMotion(artworkPlaybackScale(true), 220f)
        scope.launch { motion.update(artworkPlaybackScale(false), allowInteractionMotion(false, 6)) }
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        assertTrue(motion.progress.value < 1f && motion.progress.value > artworkPlaybackScale(false))
        repeat(150) { frames.step(this, 16) }
        assertEquals(.82f, motion.progress.value, .001f)
        scope.launch { motion.update(artworkPlaybackScale(true), allowInteractionMotion(false, 6)) }
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        assertTrue(motion.progress.value > .82f && motion.progress.value < 1f)
        repeat(150) { frames.step(this, 16) }
        assertEquals(1f, motion.progress.value, .001f)
        assertEquals(0, frames.pendingRequests)
    }

    @Test fun scrubbingEmphasisAppearsAndSettlesUnderLightHeat() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = MusicFeedbackMotion(0f, 500f)
        scope.launch { motion.update(timelineScrubEmphasis(true), allowInteractionMotion(false, 1)) }
        frames.step(this, 1)
        repeat(5) { frames.step(this, 16) }
        assertTrue(motion.progress.value > 0f && motion.progress.value < 1f)
        repeat(120) { frames.step(this, 16) }
        assertEquals(1f, motion.progress.value, .001f)
        scope.launch { motion.update(timelineScrubEmphasis(false), true) }
        repeat(150) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, .001f)
        assertEquals(0, frames.pendingRequests)
    }

    @Test fun reducedMotionStillShowsScrubbingFeedbackImmediately() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = MusicFeedbackMotion(0f, 500f)
        scope.launch { motion.update(timelineScrubEmphasis(true), false) }
        runCurrent()
        assertEquals(1f, motion.progress.value, 0f)
        assertEquals(0, frames.pendingRequests)
        scope.launch { motion.update(timelineScrubEmphasis(false), false) }
        runCurrent()
        assertEquals(0f, motion.progress.value, 0f)
    }
}
