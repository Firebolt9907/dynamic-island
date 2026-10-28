package com.island.dynamicisland.power

import org.junit.Assert.*
import org.junit.Test

class CompactTickerPolicyTest {
    @Test fun compactMotionResumesOnlyWithNormalThermalsAndBatterySaverOff() {
        assertTrue(allowCompactTicker(false, 0))
        assertFalse(allowCompactTicker(true, 0))
        for (status in 1..6) assertFalse(allowCompactTicker(false, status))
        assertTrue(allowCompactTicker(false, 0))
    }
    @Test fun briefInteractionFeedbackIsNotDisabledByTheCompactTickerHeatGuard() {
        assertTrue(allowInteractionMotion(false, 0))
        assertTrue(allowInteractionMotion(false, 1))
        assertTrue(allowInteractionMotion(false, 2))
        assertFalse(allowCompactTicker(false, 1))
        for (status in 0..6) {
            assertTrue(allowInteractionMotion(false, status))
            assertTrue(allowIslandMotion(false, status, compact = false))
            assertFalse(allowInteractionMotion(true, status))
        }
        assertFalse(allowInteractionMotion(true, 0))
    }
}
