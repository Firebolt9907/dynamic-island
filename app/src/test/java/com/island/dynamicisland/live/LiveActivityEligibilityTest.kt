package com.island.dynamicisland.live

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveActivityEligibilityTest {
    @Test fun earlyRejectionNeverDropsAnEligibleRunningOrPausedActivity() {
        val flags = listOf(false, true)
        for (ongoing in flags) for (summary in flags) for (media in flags) {
            val candidate = canBeLiveActivityNotification(ongoing, summary, media)
            for (autoCancel in flags) for (paused in flags) {
                val eligible = isLiveActivityNotification(ongoing, summary, media, autoCancel, paused)
                if (eligible) assertTrue(candidate)
                if (!candidate) assertFalse(eligible)
            }
        }
        assertTrue(canBeLiveActivityNotification(true, false, false))
        assertFalse(canBeLiveActivityNotification(false, false, false))
        assertFalse(canBeLiveActivityNotification(true, true, false))
        assertFalse(canBeLiveActivityNotification(true, false, true))
    }

    @Test fun recorderAndOtherOngoingActivitiesAreEligible() {
        // Recorder has ongoing + foreground-service + no-clear flags, a transport
        // category and a custom media layout, but no media session token.
        assertTrue(isLiveActivityNotification(true, false, false, false))
    }

    @Test fun temporaryNotificationsNeverQualify() {
        // Promotion requests, stopwatch text, foreground-service and no-clear flags
        // cannot substitute for the ongoing bit.
        assertFalse(isLiveActivityNotification(false, false, false, false))
        assertFalse(isLiveActivityNotification(true, false, false, true))
    }

    @Test fun pausedOngoingStopwatchRemainsEligibleWithAutoCancelFlag() {
        assertTrue(isLiveActivityNotification(true, false, false, true, pausedStopwatch = true))
        assertTrue(isLiveActivityNotification(true, false, false, false, pausedStopwatch = true))
        assertFalse(isLiveActivityNotification(true, true, false, true, pausedStopwatch = true))
        assertFalse(isLiveActivityNotification(true, false, true, true, pausedStopwatch = true))
        assertFalse(isLiveActivityNotification(false, false, false, false, pausedStopwatch = true))
        assertFalse(isLiveActivityNotification(false, false, false, true, pausedStopwatch = true))
    }

    @Test fun sportsFinalRemainsEligibleEvenWhenNonOngoingOrAutoCancel() {
        assertTrue(isLiveActivityNotification(false, false, false, false, sportsFinal = true))
        assertTrue(isLiveActivityNotification(false, false, false, true, sportsFinal = true))
        assertTrue(isLiveActivityNotification(true, false, false, false, sportsFinal = true))
        assertTrue(isLiveActivityNotification(true, false, false, true, sportsFinal = true))
        assertFalse(isLiveActivityNotification(false, true, false, false, sportsFinal = true))
        assertFalse(isLiveActivityNotification(false, false, true, false, sportsFinal = true))
    }

    @Test fun summariesAndMediaDoNotDuplicateActivities() {
        assertFalse(isLiveActivityNotification(true, true, false, false))
        assertFalse(isLiveActivityNotification(true, false, true, false))
    }
}
