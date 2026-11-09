package com.island.dynamicisland.ui.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CameraReflowMotionTest {
    private val slot = ActivityPlacement(-80f, 40f, 1f, 1f, 1f)

    @Test fun absorptionEndsLensSizedAndInvisibleAtTheCamera() {
        assertEquals(slot, slot.absorbed(3f, 16f, 0f))
        val inside = slot.absorbed(3f, 16f, 1f)
        assertEquals(3f, inside.center, .001f)
        assertEquals(16f, inside.width, .001f)
        assertEquals(0f, inside.alpha, .001f)
        assertEquals(0f, inside.detail, .001f)
    }

    @Test fun contentGivesWayBeforeTheSilhouetteDisappears() {
        val partway = slot.absorbed(0f, 16f, .5f)
        assertEquals(0f, partway.detail, .001f)
        assertEquals(1f, partway.alpha, .001f)
        assertTrue(slot.absorbed(0f, 16f, .85f).alpha in .01f.. .99f)
    }

    @Test fun collapseHidesEveryActivityThenNewLayoutEmerges() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        val before = activityPlacements(IslandActivities("ride", listOf("music")), 108f, 40f)
        val after = activityPlacements(IslandActivities("ride", listOf("timer", "music")), 100f, 40f)
        val motion = ActivityPlacementMotion(before, 14f, reflow = reflow)
        scope.launch { reflow.collapse(setOf("music")) }
        frames.step(this, 1)
        repeat(40) { frames.step(this, 16) }
        assertTrue(reflow.hidden)
        for (key in before.keys) assertEquals(0f, motion.frame(key, before.getValue(key)).alpha, 0f)
        scope.launch { motion.snap(after); reflow.emerge(setOf("timer", "music")) }
        frames.step(this, 1)
        frames.step(this, 120)
        // Side bubbles trail the primary out of the lens.
        assertTrue(reflow.absorption("music") > reflow.absorption("ride"))
        repeat(120) { frames.step(this, 16) }
        assertFalse(reflow.active)
        for (key in after.keys) assertEquals(after.getValue(key), motion.frame(key, after.getValue(key)))
    }

    @Test fun entranceOvershootStretchesPastTheSlotAtFullOpacity() {
        val stretched = slot.absorbed(0f, 16f, -.05f)
        assertTrue(stretched.width > slot.width)
        assertTrue(stretched.center < slot.center)
        assertEquals(1f, stretched.alpha, 0f)
        assertEquals(1f, stretched.detail, 0f)
    }

    @Test fun entranceSpringBouncesOnceAndSettlesExactly() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        repeat(40) { frames.step(this, 16) }
        assertTrue(reflow.hidden)
        scope.launch { reflow.emerge(emptySet()) }
        frames.step(this, 1)
        var deepest = 1f
        repeat(120) { frames.step(this, 16); deepest = minOf(deepest, reflow.absorption.value) }
        assertTrue("overshoot $deepest", deepest < -.01f && deepest > -.1f)
        assertEquals(0f, reflow.absorption.value, 0f)
        assertFalse(reflow.active)
    }

    @Test fun reversalKeepsTheEntranceVelocity() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        repeat(40) { frames.step(this, 16) }
        val emerging = scope.launch { reflow.emerge(emptySet()) }
        frames.step(this, 1)
        repeat(5) { frames.step(this, 16) }
        emerging.cancel()
        val before = reflow.absorption.value
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        frames.step(this, 16)
        // Still heading outward for a moment before the collapse spring wins.
        assertTrue(reflow.absorption.value < before)
    }

    @Test fun contentFadesOutBeforeTheSilhouetteAndBackInAfter() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        assertEquals(1f, reflow.contentAlpha("ride"), 0f)
        scope.launch { reflow.collapse(setOf("music")) }
        frames.step(this, 1)
        var sawPartial = false
        repeat(40) {
            frames.step(this, 16)
            val content = reflow.contentAlpha("ride")
            val silhouette = slot.absorbed(0f, 16f, reflow.absorption("ride")).alpha
            if (content in .01f.. .99f) { sawPartial = true; assertEquals(1f, silhouette, 0f) }
            if (silhouette < 1f) assertEquals(0f, content, 0f)
        }
        assertTrue(sawPartial)
        // Side bubbles empty out first.
        assertEquals(0f, reflow.contentAlpha("music"), 0f)
        scope.launch { reflow.emerge(setOf("music")) }
        frames.step(this, 1)
        repeat(120) { frames.step(this, 16) }
        assertEquals(1f, reflow.contentAlpha("ride"), 0f)
        assertEquals(1f, reflow.contentAlpha("music"), 0f)
    }

    @Test fun contentFadeIsLongEnoughToSeeInBothDirections() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        var fadingOut = 0
        repeat(40) { frames.step(this, 16); if (reflow.contentAlpha("ride") in .02f.. .98f) fadingOut++ }
        scope.launch { reflow.emerge(emptySet()) }
        frames.step(this, 1)
        var fadingIn = 0
        repeat(120) { frames.step(this, 16); if (reflow.contentAlpha("ride") in .02f.. .98f) fadingIn++ }
        assertTrue("out $fadingOut frames", fadingOut >= 6)
        assertTrue("in $fadingIn frames", fadingIn >= 6)
    }

    @Test fun contentIsBackByTheTimeTheBubbleReachesItsSlot() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        repeat(40) { frames.step(this, 16) }
        scope.launch { reflow.emerge(emptySet()) }
        frames.step(this, 1)
        var elapsed = 0
        while (reflow.absorption.value > 0f && elapsed < 1000) { frames.step(this, 16); elapsed += 16 }
        // The bubble first reaches its slot here; its content is nearly opaque by then.
        assertTrue("alpha ${reflow.contentAlpha("ride")} at ${elapsed}ms", reflow.contentAlpha("ride") > .9f)
    }

    @Test fun contentSwellsWithTheEntranceOvershootOnly() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        val layout = activityPlacements(IslandActivities("ride", listOf("music")), 108f, 40f)
        val motion = ActivityPlacementMotion(layout, 14f, reflow = reflow)
        scope.launch { reflow.collapse(setOf("music")) }
        frames.step(this, 1)
        repeat(40) {
            frames.step(this, 16)
            assertEquals(1f, motion.overshootScale("ride"), 0f)
        }
        scope.launch { reflow.emerge(setOf("music")) }
        frames.step(this, 1)
        var largest = 1f
        repeat(120) {
            frames.step(this, 16)
            val scale = motion.overshootScale("ride")
            largest = maxOf(largest, scale)
            // Content tracks exactly how far the silhouette has stretched.
            val stretched = motion.frame("ride", layout.getValue("ride")).width / layout.getValue("ride").width
            assertEquals(maxOf(1f, stretched), scale, .0001f)
        }
        assertTrue("largest $largest", largest > 1.01f && largest < 1.1f)
        assertEquals(1f, motion.overshootScale("ride"), 0f)
        assertEquals(1f, motion.overshootScale("music"), 0f)
    }

    @Test fun interruptedEmergenceCollapsesFromWhereItIs() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val reflow = CameraReflowMotion()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        repeat(40) { frames.step(this, 16) }
        val emerging = scope.launch { reflow.emerge(emptySet()) }
        frames.step(this, 1)
        frames.step(this, 100)
        val partway = reflow.absorption("ride")
        assertTrue(partway in .01f.. .99f)
        emerging.cancel()
        scope.launch { reflow.collapse(emptySet()) }
        frames.step(this, 1)
        assertEquals(partway, reflow.absorption("ride"), .02f)
        assertTrue(reflow.active)
    }
}
