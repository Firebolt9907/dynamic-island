package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SignificantLiveUpdateTest {
    private fun activity(title: String, text: String = "", status: String = "", progress: Float? = null) =
        LiveUpdate("live", "App", title, text, "", status, null, progress, false, null, emptyList())

    @Test fun expandsOnScoreAndGameStateChangesButNotClockTicks() {
        val tracker = SignificantLiveUpdateTracker()
        assertEquals("live", tracker.changedActivity(listOf(activity("BOS 102 – NYK 99", "4:32 Q4"))))
        assertNull(tracker.changedActivity(listOf(activity("BOS 102 – NYK 99", "4:31 Q4"))))
        assertEquals("live", tracker.changedActivity(listOf(activity("BOS 104 – NYK 99", "4:30 Q4"))))
        assertEquals("live", tracker.changedActivity(listOf(activity("BOS 104 – NYK 99", "Final"))))
    }

    @Test fun deliveryStageChangesExpandButEtaAndProgressDoNot() {
        val tracker = SignificantLiveUpdateTracker()
        assertEquals("live", tracker.changedActivity(listOf(activity("Preparing your order", "Arrives 7:45 PM", "15 min", .2f))))
        assertNull(tracker.changedActivity(listOf(activity("Preparing your order", "Arrives 7:46 PM", "14 min", .3f))))
        assertEquals("live", tracker.changedActivity(listOf(activity("Your order is on the way", "Arrives 7:46 PM", "14 min", .3f))))
        assertEquals("live", tracker.changedActivity(listOf(activity("Your order has arrived"))))
    }

    @Test fun repeatedNotificationsAndRecordingTicksStayQuiet() {
        val tracker = SignificantLiveUpdateTracker()
        tracker.changedActivity(listOf(activity("Recording", "00:12.45", "35%")))
        assertNull(tracker.changedActivity(listOf(activity("Recording", "00:13.12", "36%"))))
        assertNull(tracker.changedActivity(listOf(activity("Recording", "00:13.12", "36%"))))
        assertEquals("live", tracker.changedActivity(listOf(activity("Recording paused", "00:13.12", "36%"))))
    }

    @Test fun ignoresMapsServiceStatesWithoutForgettingLastDirection() {
        val tracker = SignificantLiveUpdateTracker()
        fun navigation(title: String) = activity(title).copy(isNavigation = true)
        tracker.changedActivity(listOf(navigation("Turn right onto 3rd Street")))
        assertNull(tracker.changedActivity(listOf(navigation("Rerouting..."))))
        assertNull(tracker.changedActivity(listOf(navigation("Waiting for location"))))
        assertNull(tracker.changedActivity(listOf(navigation("Turn right onto 3rd Street"))))
        assertEquals("live", tracker.changedActivity(listOf(navigation("Turn left onto Main Street"))))
    }

    @Test fun removedActivitiesDoNotKeepAnOldBaseline() {
        val tracker = SignificantLiveUpdateTracker()
        val update = activity("Preparing your order")
        tracker.changedActivity(listOf(update))
        assertNull(tracker.changedActivity(emptyList()))
        assertEquals("live", tracker.changedActivity(listOf(update)))
    }
    @Test fun foregroundUpdatesAreConsumedWithoutExpandingOnReturn() {
        val tracker = SignificantLiveUpdateTracker()
        val maps = activity("Turn right").copy(isNavigation = true)
        assertNull(tracker.changedActivity(listOf(maps), setOf("live")))
        val next = maps.copy(title = "Turn left")
        assertNull(tracker.changedActivity(listOf(next), setOf("live")))
        assertNull(tracker.changedActivity(listOf(next)))
        assertEquals("live", tracker.changedActivity(listOf(next.copy(title = "Head west"))))
    }

    @Test fun hiddenMapsUpdatesDoNotMaskOtherActivities() {
        val tracker = SignificantLiveUpdateTracker()
        val maps = activity("Turn right").copy(isNavigation = true)
        val delivery = activity("Delivering").copy(key = "delivery")
        assertEquals("delivery", tracker.changedActivity(listOf(delivery, maps), setOf("live")))
    }

    @Test fun stopwatchStaysCompactWhenPostedRepostedPausedOrResumed() {
        val tracker = SignificantLiveUpdateTracker()
        val running = activity("Stopwatch", "00:12").copy(stopwatch = Stopwatch(1_000L))
        assertNull(tracker.changedActivity(listOf(running)))
        assertNull(tracker.changedActivity(listOf(running.copy(text = "00:13"))))
        assertNull(tracker.changedActivity(listOf(running.copy(title = "Paused", stopwatch = Stopwatch(null, 13_000L)))))
        tracker.changedActivity(emptyList())
        assertNull(tracker.changedActivity(listOf(running)))
        val delivery = activity("Your order has arrived").copy(key = "delivery")
        assertEquals("delivery", tracker.changedActivity(listOf(delivery, running)))
    }
    @Test fun parsedSportsScoreChangesExpandEvenWhenNotificationTextDoesNot() {
        val tracker = SignificantLiveUpdateTracker()
        val original = activity("TENN vs SC").copy(sportsScore = SportsScore("TENN", "24", "SC", "17", "9:28 - 4th"))
        tracker.changedActivity(listOf(original))
        assertNull(tracker.changedActivity(listOf(original.copy(text = "Passing Leaders",
            sportsScore = original.sportsScore!!.copy(status = "9:27 - 4th", secondaryStatus = "4th & 16\n50")))))
        val touchdown = original.copy(sportsScore = original.sportsScore!!.copy(scoreOne = "30"))
        assertEquals("live", tracker.changedActivity(listOf(touchdown)))
        assertNull(tracker.changedActivity(listOf(touchdown)))
        assertEquals("live", tracker.changedActivity(listOf(touchdown.copy(
            sportsScore = touchdown.sportsScore!!.copy(scoreTwo = "20")))))
    }

    @Test fun sportsScoreCorrectionsExpandAndSuppressedScoresAreConsumed() {
        val tracker = SignificantLiveUpdateTracker()
        val original = activity("Game").copy(sportsScore = SportsScore("A", "7", "B", "3"))
        tracker.changedActivity(listOf(original))
        val correction = original.copy(sportsScore = original.sportsScore!!.copy(scoreOne = "6"))
        assertEquals("live", tracker.changedActivity(listOf(correction)))
        val next = correction.copy(sportsScore = correction.sportsScore!!.copy(scoreTwo = "6"))
        assertNull(tracker.changedActivity(listOf(next), setOf("live")))
        assertNull(tracker.changedActivity(listOf(next)))
    }
    @Test fun autoExpansionFadesTheChangedStageWithoutFadingEtaRefresh() {
        val before = activity("Preparing your order", "Arrives 7:45 PM")
        val after = before.copy(title = "Your order is on the way", text = "Arrives 7:46 PM")
        assertEquals(setOf("headline"), autoExpansionTextChanges(before, after))
        assertTrue(autoExpansionTextChanges(after, after.copy(text = "Arrives 7:47 PM")).isEmpty())
    }

    @Test fun autoExpansionDoesNotFadeRoutineNavigationDistance() {
        val before = activity("Turn right onto Main Street").copy(isNavigation = true, turnDistance = "200 ft")
        assertTrue(autoExpansionTextChanges(before, before.copy(turnDistance = "100 ft")).isEmpty())
        assertEquals(setOf("headline"), autoExpansionTextChanges(before,
            before.copy(title = "Turn left onto Oak Street", turnDistance = "50 ft")))
    }

    @Test fun autoExpansionFadesBodyWhenItSuppliesTheChangedHeadline() {
        val before = activity("App", "Preparing your order")
        assertEquals(setOf("headline", "text"),
            autoExpansionTextChanges(before, before.copy(text = "Your order is on the way")))
    }
}
