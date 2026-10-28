package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Test

class CompactLiveStatusTest {
    @Test fun preservesUsefulCriticalTextLongerThanFiveCharacters() {
        assertEquals("Arriving", compactLiveStatus("Arriving", .65f, "", ""))
    }
    @Test fun showsMeasuredProgress() {
        assertEquals("65%", compactLiveStatus("", .65f, "", ""))
    }
    @Test fun extractsExplicitDurationWithoutGuessingFromOtherNumbers() {
        assertEquals("3 min", compactLiveStatus("", null, "Alex is 3 minutes away", ""))
        assertEquals("", compactLiveStatus("", null, "Blue sedan ABC 1234", ""))
    }
    @Test fun preservesAndExtractsSportsScores() {
        assertEquals("21 - 17", compactLiveStatus("21 - 17", null, "", ""))
        assertEquals("104 - 98", compactLiveStatus("104 - 98", null, "", ""))
        assertEquals("0 - 0", compactLiveStatus("0 - 0", null, "", ""))
        assertEquals("21 - 17", compactLiveStatus("", null, "TENN 21 - 17 SC", "Halftime"))
        assertEquals("104 - 98", compactLiveStatus("", null, "Final: 104 - 98", ""))
        assertEquals("3 - 2", compactLiveStatus("", null, "3 - 2", "Bottom 9th"))
    }
    @Test fun doesNotTruncateLongStatusOrInventElapsedTime() {
        assertEquals("", compactLiveStatus("Mirroring your phone", null, "Mirroring Pixel", ""))
    }
    @Test fun preservesWholeDecimalAndGroupedDurations() {
        assertEquals("1.5 min", compactLiveStatus("", null, "Arriving in 1.5 minutes", ""))
        assertEquals("1,5 hr", compactLiveStatus("", null, "About 1,5 hours remaining", ""))
        assertEquals("1,000 sec", compactLiveStatus("", null, "1,000 seconds remaining", ""))
        assertEquals("1234 sec", compactLiveStatus("", null, "1234 seconds remaining", ""))
        assertEquals("", compactLiveStatus("", null, "Model X5 minutes edition", ""))
    }
    @Test fun extractsNextTurnDistances() {
        assertEquals("500 ft", nextTurnDistance("500 feet", "Turn right onto 3rd Street"))
        assertEquals("0.5 mi", nextTurnDistance("In 0.5 miles, turn left"))
        assertEquals("1,000 ft", nextTurnDistance("1,000 ft · Turn right"))
        assertEquals("200 m", nextTurnDistance("In 200 metres"))
        assertEquals(null, nextTurnDistance("Turn right onto 3rd Street", "Arrive 1:16 PM"))
    }
    @Test fun groupedNavigationDistancesKeepTheirWholeValueAndDoNotReopen() {
        assertEquals("1.000,5 m", nextTurnDistance("In 1.000,5 meters, turn right"))
        assertEquals(navigationDirectionKey("In 1.000,5 meters, turn right onto Main Street"),
            navigationDirectionKey("In 999,5 meters, turn right onto Main Street"))
        assertEquals("1,000,000 m", nextTurnDistance("In 1,000,000 meters, turn right"))
        assertEquals(navigationDirectionKey("In 1,000,000 meters, turn right onto Main Street"),
            navigationDirectionKey("In 999,999 meters, turn right onto Main Street"))
        assertEquals(null, nextTurnDistance("Model X5 km edition"))
    }
    @Test fun distanceTicksDoNotCountAsNewDirections() {
        assertEquals(navigationDirectionKey("In 500 ft, turn right onto 3rd Street"),
            navigationDirectionKey("In 200 ft, turn right onto 3rd Street"))
        org.junit.Assert.assertNotEquals(navigationDirectionKey("Turn right onto 3rd Street"),
            navigationDirectionKey("Turn left onto Main Street"))
    }
    @Test fun locationAndReroutingStatusesDoNotTriggerExpansion() {
        listOf("Waiting for location", "Waiting for location…", "WAITING FOR LOCATION",
            "Rerouting", "Rerouting...", "rerouting…").forEach {
            assertEquals("", navigationDirectionKey(it))
        }
        assertEquals("turn right onto 3rd street", navigationDirectionKey("Turn right onto 3rd Street"))
    }
}
