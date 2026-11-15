package com.island.dynamicisland.ui.island

import com.island.dynamicisland.live.CompactStatusGlyph
import org.junit.Assert.*
import org.junit.Test

class AgentCompactPartsTest {
    @Test fun splitBadgesShareCountVisibility() {
        for (counts in listOf(2 to 1, 1 to 2, 1 to 1)) {
            val parts = splitAgentCompactParts(listOf(
                CompactStatusGlyph.DONE to counts.first,
                CompactStatusGlyph.FAILED to counts.second))!!
            assertTrue(parts.showCounts)
        }
        assertFalse(AgentCompactParts(emptyList(), listOf(CompactStatusGlyph.DONE to 1)).showCounts)
    }

    @Test fun everyPairOfBadgeTypesHidesLogoAndSplitsAroundCamera() {
        val glyphs = CompactStatusGlyph.entries
        for (first in glyphs.indices) for (second in first + 1 until glyphs.size) {
            val segments = listOf(glyphs[first] to 2, glyphs[second] to 1)
            val parts = splitAgentCompactParts(segments)!!
            assertEquals(segments.take(1), parts.leading)
            assertEquals(segments.drop(1), parts.trailing)
            assertEquals(AgentCompactParts(emptyList(), segments), splitAgentCompactParts(segments, grouped = true))
        }
    }

    @Test fun singleBadgeKeepsLogoExceptInSecondaryBubble() {
        val segments = listOf(CompactStatusGlyph.DONE to 2)
        assertNull(splitAgentCompactParts(segments))
        assertEquals(AgentCompactParts(emptyList(), segments), splitAgentCompactParts(segments, secondary = true))
    }
}
