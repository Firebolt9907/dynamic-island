package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class SportsDetailsTest {
    @Test fun scoringExplanationsRemainVisibleWhenStatsAreCollapsed() {
        assertFalse(isSportsStatsSection("(Chad Ryland Kick)", true))
        assertFalse(isSportsStatsSection("Touchdown", true))
        assertFalse(isSportsStatsSection("Field Goal", false))
        assertTrue(isSportsStatsSection("Receiving Leaders", true))
        assertTrue(isSportsStatsSection("Rushing", true))
        assertTrue(isSportsStatsSection(null, true))
        assertFalse(isSportsStatsSection(null, false))
    }
}
