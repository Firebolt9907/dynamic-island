package com.island.dynamicisland.ui.island

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SportsScoreChangeTest {
    @Test fun holdsOldScoreUntilExpandedAndPointsAnimationFinishes() = runTest {
        val frames = MotionTestFrames()
        val ready = CompletableDeferred<Boolean>()
        val change = SportsScoreChange("24", "17")
        var haptics = 0
        val job = launch(frames.clock) { change.update("30", "17", onCelebration = { haptics++ }) { ready.await() } }
        runCurrent()
        assertEquals("24", change.one)
        assertEquals(6, change.deltaOne)
        assertEquals(0f, change.progress.value, 0f)
        assertEquals(0, haptics)
        ready.complete(true)
        repeat(25) { frames.step(this, 16) }
        assertEquals("24", change.one)
        assertTrue(change.progress.value > 0f)
        assertEquals(1, haptics)
        repeat(40) { frames.step(this, 16) }
        job.join()
        assertEquals("30", change.one)
        assertEquals(0, change.deltaOne)
        assertEquals(1, haptics)
    }

    @Test fun quickSuccessiveUpdatesCombineFromTheStillVisibleScore() = runTest {
        val frames = MotionTestFrames()
        val change = SportsScoreChange("24", "17")
        val first = launch(frames.clock) { change.update("30", "17") { true } }
        repeat(12) { frames.step(this, 16) }
        first.cancelAndJoin()
        val next = launch(frames.clock) { change.update("31", "17") { true } }
        frames.step(this, 16)
        assertEquals("24", change.one)
        assertEquals(7, change.deltaOne)
        repeat(70) { frames.step(this, 16) }
        next.join()
        assertEquals("31", change.one)
    }

    @Test fun correctionsAndUnpresentableChangesDoNotFreezeOrCelebrate() = runTest {
        val change = SportsScoreChange("24", "17")
        change.update("23", "17", onCelebration = { error("No correction haptic") }) { error("Correction must not wait for expansion") }
        assertEquals("23", change.one)
        change.update("30", "17", onCelebration = { error("No hidden haptic") }) { false }
        assertEquals("30", change.one)
        assertEquals(0, change.deltaOne)
        change.update("Final", "17") { error("Nonnumeric changes must not celebrate") }
        assertEquals("Final", change.one)
    }

    @Test fun independentlyTracksBothTeamsAndIgnoresInvalidNumbers() = runTest {
        assertEquals(3, scoreIncrease("17", "20"))
        assertEquals(0, scoreIncrease("20", "17"))
        assertEquals(0, scoreIncrease("-", "1"))
        val change = SportsScoreChange("24", "17")
        val gate = CompletableDeferred<Boolean>()
        val job = launch { change.update("25", "20") { gate.await() } }
        runCurrent()
        assertEquals(1, change.deltaOne)
        assertEquals(3, change.deltaTwo)
        gate.complete(false)
        job.join()
        assertEquals("25", change.one)
        assertEquals("20", change.two)
    }
}
