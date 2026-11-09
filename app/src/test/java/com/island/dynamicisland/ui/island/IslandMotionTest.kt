package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.VectorConverter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class IslandMotionTest {
    @Test fun automaticReplacementFinishesExitBeforeStartingEntrance() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val motion = IslandMotion(scope)
        motion.expandAutomatically(fromSecondary = true, mergeCompanions = true)
        repeat(150) { frames.step(this, 16) }
        var switched = false
        scope.launch {
            motion.dismissForReplacement()
            assertEquals(0f, motion.fraction, 0f)
            assertEquals(0f, motion.transfer.value, 0f)
            switched = true
            motion.expandAutomatically(fromSecondary = false, mergeCompanions = true)
        }
        frames.step(this, 16)
        repeat(5) { frames.step(this, 16) }
        assertFalse(switched)
        assertTrue(motion.fraction > 0f && motion.fraction < 1f)
        repeat(200) { frames.step(this, 16) }
        assertTrue(switched)
        assertTrue(motion.expanded)
        assertTrue(motion.automaticallyExpanded)
        assertFalse(motion.secondary)
        assertEquals(1f, motion.fraction, 0f)
    }

    @Test fun musicPeekMergesBeforeOpeningAndRestoresGroupDuringCollapse() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.peekMusic(fromSecondary = true, mergeCompanions = false, mergePair = true)
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        assertTrue(motion.musicPeek)
        assertEquals(0f, motion.fraction, .001f)
        assertTrue(motion.transfer.value > 0f)
        repeat(100) { frames.step(this, 16) }
        assertEquals(1f, motion.fraction, .001f)
        motion.dismiss()
        frames.step(this, 1)
        repeat(6) { frames.step(this, 16) }
        assertTrue(motion.transfer.value > 0f)
        assertTrue(motion.musicPeek)
        repeat(150) { frames.step(this, 16) }
        assertFalse(motion.musicPeek)
        assertFalse(motion.secondary)
        assertFalse(motion.previewActive)
    }

    @Test fun eitherPillOverlapsPairMergeWithExpansionAndReversesOnDismissal() = runTest {
        for (fromSecondary in listOf(false, true)) {
            val frames = MotionTestFrames()
            val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
            motion.press(true, 500L, fromSecondary = fromSecondary, mergePair = true) { }
            frames.step(this, 1)
            repeat(8) {
                frames.step(this, 16)
                assertTrue(motion.mergingPair)
                assertTrue(motion.mergingCompanions)
                assertEquals(0f, motion.fraction, 0f)
            }
            repeat(8) { frames.step(this, 16) }
            assertTrue(motion.fraction > 0f)
            assertTrue(motion.transfer.value > 0f && motion.transfer.value < 1f)
            repeat(120) { frames.step(this, 16) }
            motion.recognizeLongPress()
            assertEquals(1f, motion.transfer.value, .001f)
            assertEquals(1f, motion.fraction, .001f)
            assertTrue(motion.expanded)
            motion.dismiss()
            var returnedDuringCollapse = false
            repeat(150) {
                frames.step(this, 16)
                if (motion.fraction > 0f && motion.transfer.value < 1f &&
                    1f - activityHandoffFraction(motion.transfer.value) > 0f) returnedDuringCollapse = true
            }
            assertTrue("Pair companion must appear during collapse", returnedDuringCollapse)
            assertFalse(motion.mergingPair)
            assertEquals(0f, motion.transfer.value, .001f)
        }
    }

    @Test fun quickSecondaryTapDoesNotStartMergingCompanions() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true, mergePair = true) { }
        frames.step(this, 1)
        frames.step(this, 48)
        assertEquals(0f, motion.transfer.value, 0f)
        assertEquals(IslandMotion.TapResult.OPEN_APP, motion.releaseTap())
        repeat(100) { frames.step(this, 16) }
        assertEquals(0f, motion.transfer.value, 0f)
        assertFalse(motion.previewActive)
    }

    @Test fun pairHandoffIsContinuousAndComplementary() {
        assertEquals(0f, activityHandoffFraction(.55f), 0f)
        assertEquals(.5f, activityHandoffFraction(.775f), .0001f)
        assertEquals(1f, activityHandoffFraction(1f), 0f)
        var previous = 0f
        for (step in 0..100) {
            val alpha = activityHandoffFraction(step / 100f)
            assertTrue(alpha >= previous)
            assertTrue(alpha - previous < .04f)
            assertEquals(1f, alpha + (1f - alpha), .0001f)
            previous = alpha
        }
    }

    @Test fun secondaryDismissalRestoresCompanionsBeforeTheSideCardFinishesCollapsing() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true, mergeCompanions = true) { }
        repeat(150) { frames.step(this, 16) }
        motion.recognizeLongPress()
        assertFalse(motion.returningFromCamera)
        motion.dismiss()
        var sawCameraReturn = false
        var overlappedReturn = false
        repeat(150) {
            frames.step(this, 16)
            if (motion.returningFromCamera) {
                sawCameraReturn = true
                assertTrue(motion.fraction <= .65f)
                if (motion.fraction > 0f && motion.transfer.value < 1f) overlappedReturn = true
                assertTrue(motion.secondary)
            }
        }
        assertTrue(sawCameraReturn)
        assertTrue("Companions must return before the side card is compact", overlappedReturn)
        assertFalse(motion.returningFromCamera)
        assertFalse(motion.secondary)
    }

    @Test fun automaticNavigationExpansionSupportsSecondaryAndDismissal() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.expandAutomatically(fromSecondary = true, mergeCompanions = false)
        repeat(150) { frames.step(this, 16) }
        assertTrue(motion.expanded)
        assertTrue(motion.secondary)
        assertEquals(1f, motion.fraction, .001f)
        assertEquals(1f, motion.transfer.value, .001f)
        motion.dismiss()
        repeat(150) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertFalse(motion.secondary)
        assertEquals(0f, motion.fraction, .001f)
    }

    @Test fun compactDragTracksFingerAndShortPullReturnsExpanded() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        repeat(100) { frames.step(this, 16) }
        motion.recognizeLongPress()
        assertTrue(motion.beginCompactDrag())
        motion.dragCompact(-20f, 200f)
        assertEquals(.9f, motion.fraction, .001f)
        assertFalse(motion.finishCompactDrag(0f, 200f))
        repeat(100) { frames.step(this, 16) }
        assertTrue(motion.expanded)
        assertEquals(1f, motion.fraction, .001f)
    }

    @Test fun slowCompactDragCommitsWithoutFlingAndDoesNotLeaveFrozenProgress() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.expandAutomatically(fromSecondary = false, mergeCompanions = false)
        repeat(150) { frames.step(this, 16) }
        assertTrue(motion.beginCompactDrag())
        // A slow pull must finish using its displacement, with no release velocity.
        repeat(40) {
            motion.dragCompact(-2f, 200f)
            frames.step(this, 32)
        }
        assertEquals(.6f, motion.fraction, .001f)
        assertTrue(motion.finishCompactDrag(0f, 200f))
        repeat(150) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertEquals(0f, motion.fraction, .001f)
        // Opening again must use the animation rather than a stale drag override.
        motion.expandAutomatically(fromSecondary = false, mergeCompanions = false)
        repeat(150) { frames.step(this, 16) }
        assertEquals(1f, motion.fraction, .001f)
    }

    @Test fun upwardFlickCollapsesAndCancelledDragRestoresExpanded() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true) { }
        repeat(150) { frames.step(this, 16) }
        motion.recognizeLongPress()
        assertTrue(motion.beginCompactDrag())
        motion.dragCompact(-100f, 200f)
        assertFalse(motion.finishCompactDrag(-1000f, 200f, cancelled = true))
        repeat(100) { frames.step(this, 16) }
        assertTrue(motion.expanded)
        assertEquals(1f, motion.fraction, .001f)
        assertTrue(motion.beginCompactDrag())
        motion.dragCompact(-20f, 200f)
        assertTrue(motion.finishCompactDrag(-1000f, 200f))
        repeat(150) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertFalse(motion.secondary)
        assertFalse(motion.previewActive)
        assertEquals(0f, motion.fraction, .001f)
    }

    @Test fun quickReleaseBouncesWithoutRevealingExpandedContent() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        var haptics = 0
        motion.press(true, 500L) { haptics++ }
        frames.step(this, 1)
        repeat(10) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0f)
        val heldScale = motion.pressScale.value
        assertTrue(heldScale > 1f)
        assertEquals(IslandMotion.TapResult.OPEN_APP, motion.releaseTap())
        var peak = heldScale
        repeat(80) {
            frames.step(this, 16)
            peak = maxOf(peak, motion.pressScale.value)
            assertEquals(0f, motion.progress.value, 0f)
        }
        assertTrue(peak > heldScale)
        assertEquals(1f, motion.pressScale.value, 0.001f)
        assertEquals(0, haptics)
    }

    @Test fun primaryGrowsWhileBothCompanionsMergeBehindIt() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, mergeCompanions = true) { }
        frames.step(this, 1)
        frames.step(this, 100)
        assertTrue(motion.mergingCompanions)
        assertFalse(motion.secondary)
        assertTrue(motion.transfer.value > 0f)
        assertEquals(0f, motion.progress.value, 0f)
        var grewDuringMerge = false
        repeat(150) {
            frames.step(this, 16)
            if (motion.progress.value > 0f && motion.transfer.value > 0f && motion.transfer.value < 1f)
                grewDuringMerge = true
        }
        assertTrue("Primary must grow before companions finish merging", grewDuringMerge)
        motion.recognizeLongPress()
        motion.dismiss()
        var returnedDuringCollapse = false
        repeat(150) {
            frames.step(this, 16)
            if (motion.transfer.value < 1f && motion.progress.value > 0f) {
                assertTrue(motion.progress.value <= .65f)
                returnedDuringCollapse = true
            }
        }
        assertTrue("Side bubbles must return while the primary shrinks", returnedDuringCollapse)
        assertFalse(motion.transferringActivities)
        assertEquals(0f, motion.transfer.value, 0f)
    }

    @Test fun cancelledThreeActivityHoldReturnsBothCompanions() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true, mergeCompanions = true) { }
        frames.step(this, 1)
        frames.step(this, 100)
        motion.cancelPress()
        repeat(150) { frames.step(this, 16) }
        assertEquals(0f, motion.transfer.value, 0f)
        assertFalse(motion.mergingCompanions)
        assertFalse(motion.secondary)
    }

    @Test fun stopwatchPreviewRunsFromTouchDownThroughFinalCollapse() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        assertFalse(motion.previewActive)
        motion.press(true, 500L) { }
        assertTrue(motion.previewActive)
        assertEquals(0f, motion.progress.value, 0f)
        assertFalse(motion.expanded)
        repeat(100) { frames.step(this, 16) }
        motion.recognizeLongPress()
        motion.dismiss()
        assertTrue(motion.previewActive)
        frames.step(this, 16)
        assertTrue(motion.previewActive)
        repeat(150) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0f)
        assertFalse(motion.previewActive)
    }

    @Test fun cancelledSecondaryKeepsPreviewActiveUntilItReturnsToItsBubble() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true) { }
        assertTrue(motion.previewActive)
        frames.step(this, 1)
        frames.step(this, 100)
        motion.cancelPress()
        assertTrue(motion.previewActive)
        repeat(150) { frames.step(this, 16) }
        assertFalse(motion.previewActive)
        assertEquals(0f, motion.transfer.value, 0f)
    }

    @Test fun secondaryGrowsDuringPromotionAndRestoresGroupWhileCollapsing() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true) { }
        frames.step(this, 1)
        frames.step(this, 100)
        assertTrue(motion.transfer.value > 0f)
        assertTrue(motion.transfer.value < 1f)
        assertEquals(0f, motion.progress.value, 0f)
        var overlapped = false
        repeat(150) {
            frames.step(this, 16)
            if (motion.progress.value > 0f && motion.transfer.value < 1f) overlapped = true
        }
        assertTrue("Growth should join the inward movement before it settles", overlapped)
        motion.recognizeLongPress()
        assertTrue(motion.expanded)
        motion.dismiss()
        repeat(150) {
            frames.step(this, 16)
            if (motion.transfer.value < 1f) assertTrue(motion.progress.value <= .65f)
        }
        assertEquals(0f, motion.transfer.value, 0f)
        assertFalse(motion.secondary)
    }

    @Test fun cancelledSecondaryPromotionReversesWithoutExpanding() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L, fromSecondary = true) { }
        frames.step(this, 1)
        frames.step(this, 100)
        motion.cancelPress()
        repeat(150) {
            frames.step(this, 16)
            assertFalse(motion.returningFromCamera)
            assertEquals(0f, motion.progress.value, 0f)
        }
        assertFalse(motion.secondary)
        assertEquals(0f, motion.transfer.value, 0f)
        assertFalse(motion.expanded)
    }

    @Test fun releaseAfterVisibleExpansionKeepsOpenBeforeSystemLongPressTimeout() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        repeat(30) { frames.step(this, 16) }
        assertTrue(motion.progress.value > IslandGrowth.hapticProgress)
        assertEquals(IslandMotion.TapResult.KEEP_EXPANDED, motion.releaseTap())
        repeat(80) { frames.step(this, 16) }
        assertTrue(motion.expanded)
        assertEquals(1f, motion.progress.value, 0.002f)
        // A duplicate callback cannot reinterpret that release as a collapse or app launch.
        assertEquals(IslandMotion.TapResult.IGNORED, motion.releaseTap())
    }

    @Test fun quickTapLaunchesButSubsequentTapOnOpenCardCollapses() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        frames.step(this, 40)
        assertEquals(IslandMotion.TapResult.OPEN_APP, motion.releaseTap())
        repeat(100) { frames.step(this, 16) }
        motion.press(true, 500L) { }
        frames.step(this, 1)
        repeat(30) { frames.step(this, 16) }
        motion.releaseTap()
        motion.press(true, 500L) { }
        assertEquals(IslandMotion.TapResult.COLLAPSED, motion.releaseTap())
        repeat(100) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertEquals(0f, motion.progress.value, 0.002f)
    }

    @Test fun cancelledOrDismissedPressCannotLaunchOrReopen() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        for (cancel in listOf(true, false)) {
            motion.press(true, 500L) { }
            frames.step(this, 1)
            repeat(30) { frames.step(this, 16) }
            if (cancel) motion.cancelPress() else motion.dismiss()
            assertEquals(IslandMotion.TapResult.IGNORED, motion.releaseTap())
            assertFalse(motion.recognizeLongPress())
            repeat(100) { frames.step(this, 16) }
            assertFalse(motion.expanded)
        }
    }

    @Test fun recognitionKeepsTheRunningAnimationAndHapticInTheSecondHalf() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        val haptics = mutableListOf<Float>()
        motion.press(true, 500L) { haptics.add(motion.progress.value) }
        frames.step(this, 1)
        repeat(50) { frames.step(this, 10) }
        val spring = IslandGrowth.spec(700).vectorize(Float.VectorConverter)
        fun expected(ms: Long) = spring.getValueFromNanos(ms * 1_000_000L,
            AnimationVector1D(0f), AnimationVector1D(1f), AnimationVector1D(0f)).value
        assertEquals(expected(270), motion.progress.value, 0.002f)
        val beforeRecognition = motion.progress.value
        motion.commit()
        assertEquals(beforeRecognition, motion.progress.value, 0f)
        frames.step(this, 100)
        assertEquals(expected(370), motion.progress.value, 0.002f)
        repeat(60) { frames.step(this, 16) }
        assertEquals(1f, motion.progress.value, 0.002f)
        assertTrue(motion.expanded)
        assertEquals(1, haptics.size)
        assertTrue(haptics.single() >= 0.5f && haptics.single() < 1f)
    }

    @Test fun cancelledHoldReturnsHomeAndNeverEmitsTheExpansionHaptic() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        var haptics = 0
        motion.press(true, 500L) { haptics++ }
        frames.step(this, 1)
        frames.step(this, 75)
        assertEquals(0f, motion.progress.value, 0f)
        assertTrue(motion.pressScale.value > 1f)
        motion.cancelPress()
        repeat(100) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0.002f)
        assertFalse(motion.expanded)
        assertEquals(0, haptics)
    }

    @Test fun reopeningDuringCollapsePreservesTheMovingSpringsVelocity() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        repeat(30) { frames.step(this, 16) }
        motion.dismiss()
        frames.step(this, 1)
        frames.step(this, 80)
        val position = motion.progress.value
        val velocity = motion.progress.velocity
        assertTrue(velocity < 0f)
        motion.press(true, 500L) { }
        frames.step(this, 1)
        frames.step(this, 16)
        val expected = IslandGrowth.spec(700).vectorize(Float.VectorConverter)
            .getValueFromNanos(16_000_000L, AnimationVector1D(position),
                AnimationVector1D(1f), AnimationVector1D(velocity)).value
        assertEquals(expected, motion.progress.value, 0.002f)
        motion.commit()
        repeat(100) { frames.step(this, 16) }
        assertEquals(1f, motion.progress.value, 0.002f)
    }

    @Test fun rapidTapInterruptsGrowthWithoutAnInstantValueJump() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        frames.step(this, 40)
        val pressedSize = motion.progress.value
        motion.tapRebound()
        assertEquals(pressedSize, motion.progress.value, 0f)
        frames.step(this, 16)
        val reboundSize = motion.progress.value
        motion.press(true, 500L) { }
        assertEquals(reboundSize, motion.progress.value, 0f)
        frames.step(this, 1)
        motion.dismiss()
        repeat(100) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0.002f)
    }
    @Test fun dismissalDuringCommittedGrowthPreventsAnyLaterReopen() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        frames.step(this, 500)
        motion.commit()
        motion.dismiss()
        repeat(100) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertEquals(0f, motion.progress.value, 0.002f)
        frames.step(this, 1000)
        assertEquals(0f, motion.progress.value, 0.002f)
    }

    @Test fun aPressWithoutMediaStillSwellsAndReboundsWithoutAnExpansionHaptic() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        var haptics = 0
        motion.press(false, 500L) { haptics++ }
        frames.step(this, 1)
        frames.step(this, 100)
        assertEquals(0f, motion.progress.value, 0f)
        assertTrue(motion.pressScale.value > 1f)
        motion.tapRebound()
        repeat(100) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0.002f)
        assertEquals(0, haptics)
        assertFalse(motion.expanded)
    }

    @Test fun noMediaLongPressReturnsHomeInsteadOfLeavingTheSwellingStuck() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        var haptics = 0
        motion.press(false, 500L) { haptics++ }
        frames.step(this, 1)
        repeat(32) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0f)
        assertTrue(motion.pressScale.value > 1.03f)
        motion.commit()
        repeat(100) { frames.step(this, 16) }
        assertEquals(0f, motion.progress.value, 0.002f)
        assertFalse(motion.expanded)
        assertEquals(0, haptics)
    }

    @Test fun staleRecognitionAfterDismissalCannotCommitACancelledPress() = runTest {
        val frames = MotionTestFrames()
        val motion = IslandMotion(CoroutineScope(backgroundScope.coroutineContext + frames.clock))
        motion.press(true, 500L) { }
        frames.step(this, 1)
        frames.step(this, 200)
        motion.dismiss()
        motion.commit()
        repeat(100) { frames.step(this, 16) }
        assertFalse(motion.expanded)
        assertEquals(0f, motion.progress.value, 0.002f)
    }

}
