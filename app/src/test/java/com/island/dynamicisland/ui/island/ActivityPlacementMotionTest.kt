package com.island.dynamicisland.ui.island

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityPlacementMotionTest {
    @Test fun primaryGroupingTracksItsBackgroundOnArrivalsAndRemovals() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        fun layout(count: Int) = activityPlacements(IslandActivities("espn",
            listOf("music", "ride").take(count - 1)), 140f + count * 20f, 40f)
        var previous = layout(1)
        val motion = ActivityPlacementMotion(previous, 16f)
        for (count in listOf(2, 3, 2, 1)) {
            val next = layout(count)
            val before = motion.frame("espn", previous.getValue("espn"))
            scope.launch { motion.retarget(next) }
            frames.step(this, 1)
            assertEquals(before, motion.frame("espn", next.getValue("espn")))
            frames.step(this, 240)
            val during = motion.frame("espn", next.getValue("espn"))
            val destination = next.getValue("espn")
            val geometryProgress = (during.width - before.width) / (destination.width - before.width)
            assertTrue(geometryProgress > 0f && geometryProgress < 1f)
            assertEquals(before.grouping + (destination.grouping - before.grouping) * geometryProgress,
                during.grouping, .0001f)
            frames.step(this, 1000)
            assertEquals(destination, motion.frame("espn", destination))
            previous = next
        }
    }

    @Test fun bubbleTravelHasDistinctAccelerationAndDeceleration() {
        val easing = ActivityBubbleMotion.merge.easing
        val earlyDistance = easing.transform(.15f) - easing.transform(0f)
        val middleDistance = easing.transform(.5f) - easing.transform(.35f)
        val lateDistance = easing.transform(1f) - easing.transform(.85f)
        assertTrue(middleDistance > earlyDistance * 2f)
        assertTrue(middleDistance > lateDistance * 4f)
        assertTrue(ActivityBubbleMotion.departure.durationMillis > ActivityBubbleMotion.merge.durationMillis)
    }

    @Test fun placementInterpolationUsesExactEndpointsAcrossTheCamera() {
        val left = ActivityPlacement(-91.347f, 67.6f, .6f, .7f)
        val right = ActivityPlacement(80f, 40f, 1f, 0f)
        assertEquals(left, left.towards(right, 0f))
        assertEquals(right, left.towards(right, 1f))
    }

    @Test fun stopwatchPairShowsMeasuredChronometerAlongsideItsIcon() {
        val slots = IslandActivities("sharing", listOf("stopwatch"))
        val placements = activityPlacements(slots, 96f, 40f, 40f, secondaryIndicatorWidth = 44f)
        val stopwatch = placements.getValue("stopwatch")
        assertEquals(90f, stopwatch.width, .001f)
        assertEquals(1f, stopwatch.detail, 0f)
        assertEquals(6f, (40f - 48f) - (stopwatch.center + stopwatch.width / 2f), .001f)
        val triple = activityPlacements(IslandActivities("sharing", listOf("stopwatch", "ride")),
            96f, 40f, secondaryIndicatorWidth = 44f)
        assertEquals(40f, triple.getValue("stopwatch").width, 0f)
        assertEquals(0f, triple.getValue("stopwatch").detail, 0f)
    }

    @Test fun musicPairReservesArtworkAndVisualizerWhileTripleKeepsCircles() {
        val pair = activityPlacements(IslandActivities("share", listOf("music")), 90f, 40f, 39f)
        val music = pair.getValue("music")
        assertEquals(67.6f, music.width, .001f)
        assertEquals(1f, music.detail, 0f)
        assertEquals(6f, (39f - 45f) - (music.center + music.width / 2f), .001f)
        val triple = activityPlacements(IslandActivities("share", listOf("timer", "music")), 90f, 40f)
        assertEquals(40f, triple.getValue("music").width, 0f)
        assertEquals(0f, triple.getValue("music").detail, 0f)
    }

    @Test fun eitherSideOwnsTheMergeAndPrimaryTravelsTowardIt() {
        val placements = activityPlacements(IslandActivities("primary", listOf("left", "right")), 100f, 40f)
        for (key in listOf("left", "right")) {
            val selected = placements.getValue(key)
            val primary = placements.getValue("primary")
            assertEquals(primary, mergingActivityPlacement(primary, selected, 0f))
            val halfway = mergingActivityPlacement(primary, selected, .5f)
            assertEquals((primary.center + selected.center) / 2f, halfway.center, .001f)
            assertEquals(selected.center, mergingActivityPlacement(primary, selected, 1f).center, .001f)
            assertEquals(selected, mergingActivityPlacement(selected, selected, .5f))
        }
    }

    @Test fun returningCompanionsStartAtCameraAndFinishAtTheirOwnSlots() {
        val placements = activityPlacements(IslandActivities("primary", listOf("left", "right")), 100f, 40f)
        for (key in listOf("primary", "left", "right")) {
            val destination = placements.getValue(key)
            val hidden = returningActivityPlacement(destination, 7f, 16f, 1f)
            assertEquals(7f, hidden.center, .001f)
            assertEquals(16f, hidden.width, .001f)
            val halfway = returningActivityPlacement(destination, 7f, 16f, .5f)
            assertEquals((destination.center + 7f) / 2f, halfway.center, .001f)
            assertEquals(destination, returningActivityPlacement(destination, 7f, 16f, 0f))
        }
    }

    @Test fun musicMovesFromItsExistingCenterIntoTheSideBubble() = runTest {
        val frames = MotionTestFrames()
        val initial = activityPlacements(IslandActivities("music", emptyList()), 160f, 40f)
        val target = activityPlacements(IslandActivities("ride", listOf("music")), 108f, 40f)
        val motion = ActivityPlacementMotion(initial, 14f)
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        scope.launch { motion.retarget(target) }
        frames.step(this, 1)
        assertEquals(initial.getValue("music"), motion.frame("music", target.getValue("music")))
        assertEquals(0f, motion.frame("ride", target.getValue("ride")).alpha, 0f)
        frames.step(this, 280)
        val music = motion.frame("music", target.getValue("music"))
        assertTrue(music.center < 0f && music.center > target.getValue("music").center)
        assertTrue(music.width in 41f..159f)
        assertTrue(motion.frame("ride", target.getValue("ride")).alpha in .01f.. .99f)
        // Allow the eased placement animation to finish.
        frames.step(this, 1000)
        assertEquals(target.getValue("music"), motion.frame("music", target.getValue("music")))
        assertEquals(target.getValue("ride"), motion.frame("ride", target.getValue("ride")))
    }

    @Test fun anotherArrivalContinuesFromTheCurrentPosition() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val first = activityPlacements(IslandActivities("music", emptyList()), 160f, 40f)
        val second = activityPlacements(IslandActivities("ride", listOf("music")), 108f, 40f)
        val third = activityPlacements(IslandActivities("ride", listOf("delivery", "music")), 108f, 40f)
        val motion = ActivityPlacementMotion(first, 14f)
        val job = scope.launch { motion.retarget(second) }
        frames.step(this, 1)
        frames.step(this, 180)
        val before = motion.frame("music", second.getValue("music"))
        job.cancel()
        scope.launch { motion.retarget(third) }
        frames.step(this, 1)
        assertEquals(before, motion.frame("music", third.getValue("music")))
        // Allow the eased placement animation to finish.
        frames.step(this, 1000)
        assertEquals(third.getValue("music"), motion.frame("music", third.getValue("music")))
    }
    @Test fun compactInputEnvelopeStaysFixedThroughoutAppSwitchTravel() = runTest {
        val frames = MotionTestFrames()
        val scope = CoroutineScope(backgroundScope.coroutineContext + frames.clock)
        val first = activityPlacements(IslandActivities("ride", listOf("music")), 108f, 40f)
        val next = activityPlacements(IslandActivities("music", emptyList()), 160f, 40f)
        val target = next.getValue("music")
        val motion = ActivityPlacementMotion(first, 14f)
        scope.launch { motion.retarget(next) }
        frames.step(this, 1)
        val envelope = motion.touchEnvelope("music", target)
        repeat(30) {
            frames.step(this, 16)
            val visual = motion.frame("music", target)
            assertEquals(envelope, motion.touchEnvelope("music", target))
            assertTrue(visual.center - visual.width / 2f >= envelope.center - envelope.width / 2f - .001f)
            assertTrue(visual.center + visual.width / 2f <= envelope.center + envelope.width / 2f + .001f)
        }
        frames.step(this, 1000)
        assertEquals(target, motion.touchEnvelope("music", target))
    }

}
