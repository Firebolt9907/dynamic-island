package com.island.dynamicisland.live

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveUpdateTextTest {
    @Test fun expandedTextReplacesTheCompactVersion() {
        assertEquals(LiveUpdateText("Mirroring Rishu's Pixel", ""), liveUpdateText(
            "Phone Mirroring", "Mirroring", "Mirroring Rishu's Pixel", listOf("Mirroring"), "Mirroring"))
    }
    @Test fun repeatedTitleAndWhitespaceDoNotCreateExtraLines() {
        assertEquals(LiveUpdateText("", ""), liveUpdateText("Phone Mirroring", " phone  mirroring ", "", emptyList(), "Mirroring"))
    }
    @Test fun distinctUsefulDetailIsPreserved() {
        assertEquals(LiveUpdateText("Alex is 3 minutes away", "Blue sedan · ABC 1234"), liveUpdateText(
            "Your ride", "Alex is 3 minutes away", "", emptyList(), "Blue sedan · ABC 1234"))
    }
    @Test fun textLinesAreAnAlternativeToCompactBody() {
        assertEquals(LiveUpdateText("Preparing\nDriver assigned", ""), liveUpdateText(
            "Delivery", "Preparing", "", listOf("Preparing", "Driver assigned", "Preparing"), ""))
    }
}
