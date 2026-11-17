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

class LiveBarsTest {
    @Test fun reachesAKickWithinOneCaptureAndFallsSoonAfter() {
        val bars = LiveBars(6)
        val targets = floatArrayOf(1f, 0f, 0f, 0f, 0f, .5f)
        // Three 60 Hz frames ≈ one 20 Hz capture interval.
        repeat(3) { bars.step(targets, 16.7f) }
        org.junit.Assert.assertTrue("kick should be mostly up within one capture: ${bars.values[0]}", bars.values[0] > .65f)
        repeat(3) { bars.step(targets, 16.7f) }
        org.junit.Assert.assertTrue("and near full within two: ${bars.values[0]}", bars.values[0] > .9f)
        targets.fill(0f)
        repeat(3) { bars.step(targets, 16.7f) }
        org.junit.Assert.assertTrue("bass should glide down, not drop: ${bars.values[0]}", bars.values[0] in .6f..0.8f)
        repeat(24) { bars.step(targets, 16.7f) }
        org.junit.Assert.assertTrue("and be near rest after half a second: ${bars.values[0]}", bars.values[0] < .1f)
    }

    @Test fun fewerBarsKeepBassOnTheLeft() {
        val bars = LiveBars(3)
        repeat(30) { bars.step(floatArrayOf(1f, 1f, 0f, 0f, .2f, .2f), 16.7f) }
        org.junit.Assert.assertArrayEquals(floatArrayOf(1f, 0f, .2f), bars.values, .001f)
    }
}
