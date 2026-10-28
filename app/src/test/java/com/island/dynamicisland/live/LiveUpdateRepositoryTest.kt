package com.island.dynamicisland.live

import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class LiveUpdateRepositoryTest {
    @After fun cleanup() = LiveUpdateRepository.clear()
    private fun update(key: String, text: String = "On the way") = LiveUpdate(
        key, "Rides", "Pickup", text, "", "3 min", null, .5f, false, null, emptyList())

    @Test fun updatesKeepSelectionAndRemovalRestoresNextActivity() {
        LiveUpdateRepository.put("ride", update("ride"))
        LiveUpdateRepository.put("delivery", update("delivery"))
        LiveUpdateRepository.put("ride", update("ride", "Arrived"))
        assertEquals(listOf("ride", "delivery"), LiveUpdateRepository.activities.value.map { it.key })
        assertEquals("Arrived", LiveUpdateRepository.activities.value.first().text)
        assertEquals("Arrived", LiveUpdateRepository.current.value?.text)
        LiveUpdateRepository.put("ride", null)
        assertEquals("delivery", LiveUpdateRepository.current.value?.key)
        LiveUpdateRepository.put("delivery", null)
        assertNull(LiveUpdateRepository.current.value)
        assertTrue(LiveUpdateRepository.activities.value.isEmpty())
    }

    @Test fun navigationOvertakesEarlierStopwatchAndRemovalRestoresIt() {
        val watch = update("watch").copy(stopwatch = Stopwatch(1000L))
        val maps = update("maps").copy(isNavigation = true)
        LiveUpdateRepository.put("watch", watch)
        LiveUpdateRepository.put("maps", maps)
        assertEquals(listOf("maps", "watch"), LiveUpdateRepository.activities.value.map { it.key })
        assertEquals("maps", LiveUpdateRepository.current.value?.key)
        LiveUpdateRepository.put("watch", watch.copy(text = "42:00"))
        assertEquals("maps", LiveUpdateRepository.current.value?.key)
        LiveUpdateRepository.put("maps", null)
        assertEquals("watch", LiveUpdateRepository.current.value?.key)
    }

    @Test fun mapsFallbackAndOtherNavigationAppsBothOutrankStopwatch() {
        LiveUpdateRepository.put("watch", update("watch").copy(stopwatch = Stopwatch(1000L)))
        LiveUpdateRepository.put("maps", update("maps").copy(packageName = "com.google.android.apps.maps"))
        LiveUpdateRepository.put("navigation", update("navigation").copy(isNavigation = true, packageName = "other.navigation"))
        assertEquals(listOf("maps", "navigation", "watch"), LiveUpdateRepository.activities.value.map { it.key })
    }

    @Test fun ongoingActivitiesOutrankStopwatchesAndPausedWatchesComeLast() {
        val running = update("running").copy(stopwatch = Stopwatch(1000L))
        val paused = update("paused").copy(stopwatch = Stopwatch(null, 5000L))
        LiveUpdateRepository.put("paused", paused)
        LiveUpdateRepository.put("running", running)
        LiveUpdateRepository.put("ride", update("ride"))
        LiveUpdateRepository.put("recording", update("recording").copy(progress = null))
        assertEquals(listOf("ride", "recording", "running", "paused"), LiveUpdateRepository.activities.value.map { it.key })
        LiveUpdateRepository.put("paused", paused.copy(stopwatch = Stopwatch(2000L)))
        // Same-priority peers retain their original arrival order, even on resume.
        assertEquals(listOf("ride", "recording", "paused", "running"), LiveUpdateRepository.activities.value.map { it.key })
    }

    @Test fun disconnectClearsAllActivities() {
        LiveUpdateRepository.put("ride", update("ride"))
        LiveUpdateRepository.clear()
        assertNull(LiveUpdateRepository.current.value)
        LiveUpdateRepository.put("unrelated", null)
        assertNull(LiveUpdateRepository.current.value)
        assertTrue(LiveUpdateRepository.activities.value.isEmpty())
    }

    @Test fun sportsContextSurvivesPanelRotationAndRemovalResetsIt() {
        val score = SportsScore("TENN", "24", "SC", "17", "9:28 - 4th", secondaryStatus = "4th & 16\n50")
        val activity = update("sports").copy(sportsScore = score)
        LiveUpdateRepository.put("sports", activity)
        LiveUpdateRepository.put("sports", activity.copy(sportsScore = score.copy(secondaryStatus = null)))
        assertEquals("4th & 16\n50", LiveUpdateRepository.current.value?.sportsScore?.secondaryStatus)
        LiveUpdateRepository.put("sports", null)
        LiveUpdateRepository.put("sports", activity.copy(sportsScore = score.copy(secondaryStatus = null)))
        assertNull(LiveUpdateRepository.current.value?.sportsScore?.secondaryStatus)
    }

    @Test fun sportsFinalScoreSurvivesUntilExplicitNotificationRemoval() {
        val score = SportsScore("TENN", "24", "SC", "17", "Final")
        val activity = update("sports").copy(sportsScore = score)
        LiveUpdateRepository.put("sports", activity)
        assertEquals("Final", LiveUpdateRepository.current.value?.sportsScore?.status)
        assertEquals(listOf("sports"), LiveUpdateRepository.activities.value.map { it.key })

        // When the notification is removed by user dismissal:
        LiveUpdateRepository.put("sports", null)
        assertNull(LiveUpdateRepository.current.value)
        assertTrue(LiveUpdateRepository.activities.value.isEmpty())
    }
}
