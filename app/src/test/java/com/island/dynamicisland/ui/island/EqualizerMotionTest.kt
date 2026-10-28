package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class EqualizerMotionTest {
    private class DurationScale(initial: Float) : MotionDurationScale {
        override var scaleFactor by mutableFloatStateOf(initial)
    }

    @Test fun batterySaverStopsActiveBarsAndNormalModeResumesTheSameLevels() = runTest {
        val frames = MotionTestFrames()
        val level = Animatable(0.3f)
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock + DurationScale(1f))
        val normal = scope.launch { animateEqualizer(listOf(level), playing = true) }
        frames.step(this, 1)
        frames.step(this, 1)
        frames.step(this, 200)
        assertTrue(level.value > 0.3f)
        normal.cancel()
        runCurrent()
        val saver = scope.launch {
            animateEqualizer(listOf(level), playing = true, animationsEnabled = false)
        }
        runCurrent()
        assertEquals(0.3f, level.value, 0f)
        repeat(30) { frames.step(this, 16) }
        assertEquals(0, frames.pendingRequests)
        saver.cancel()
        runCurrent()
        scope.launch { animateEqualizer(listOf(level), playing = true) }
        frames.step(this, 1)
        frames.step(this, 1)
        frames.step(this, 200)
        assertTrue(level.value > 0.3f)
    }

    @Test fun disabledAnimationUsesNoFrameCallbacksEvenWhilePlaying() = runTest {
        val frames = MotionTestFrames()
        val scale = DurationScale(0f)
        val level = Animatable(0.8f)
        CoroutineScope(backgroundScope.coroutineContext + frames.clock + scale).launch {
            animateEqualizer(listOf(level), playing = true)
        }
        runCurrent()
        assertEquals(0.3f, level.value, 0f)
        assertEquals(0, frames.pendingRequests)
        repeat(30) { frames.step(this, 16) }
        assertEquals(0, frames.pendingRequests)
    }

    @Test fun disablingAndReenablingMotionStopsAndResumesTheSameLevel() = runTest {
        val frames = MotionTestFrames()
        val scale = DurationScale(1f)
        val level = Animatable(0.3f)
        CoroutineScope(backgroundScope.coroutineContext + frames.clock + scale).launch {
            animateEqualizer(listOf(level), playing = true)
        }
        frames.step(this, 1)
        frames.step(this, 1)
        frames.step(this, 200)
        assertTrue(level.value > 0.3f)
        scale.scaleFactor = 0f
        Snapshot.sendApplyNotifications()
        runCurrent()
        assertEquals(0.3f, level.value, 0f)
        assertEquals(0, frames.pendingRequests)
        scale.scaleFactor = 1f
        Snapshot.sendApplyNotifications()
        runCurrent()
        frames.step(this, 1)
        frames.step(this, 1)
        frames.step(this, 200)
        assertTrue(level.value > 0.3f)
    }

    @Test fun pausedBarsSettleThenStopRequestingFrames() = runTest {
        val frames = MotionTestFrames()
        val level = Animatable(0.9f)
        CoroutineScope(backgroundScope.coroutineContext + frames.clock + DurationScale(1f)).launch {
            animateEqualizer(listOf(level), playing = false)
        }
        frames.step(this, 1)
        frames.step(this, 100)
        assertTrue(level.value < 0.9f && level.value > 0.3f)
        frames.step(this, 300)
        assertEquals(0.3f, level.value, 0f)
        assertEquals(0, frames.pendingRequests)
    }
}
