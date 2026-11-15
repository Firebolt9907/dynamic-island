package com.island.dynamicisland.live

import com.island.dynamicisland.config.LiveActivitySource
import com.island.dynamicisland.config.liveActivitySourceKey
import com.island.dynamicisland.config.rememberLiveActivitySource
import org.junit.Assert.*
import org.junit.Test

class LiveActivityHistoryTest {
    @Test fun updatesRetainDisabledChoiceAndRefreshTitleWithoutDuplicates() {
        val disabled = LiveActivitySource("android", "debugging", "Android", "Connected", 100, false)
        val updated = disabled.copy(title = "Disconnected", lastSeen = 200, enabled = true)
        val history = rememberLiveActivitySource(listOf(disabled), updated)
        assertEquals(1, history.size)
        assertFalse(history.single().enabled)
        assertEquals("Disconnected", history.single().title)
        assertEquals(200L, history.single().lastSeen)
    }

    @Test fun categoriesInSameAppHaveIndependentChoices() {
        val debugging = LiveActivitySource("android", "debugging", "Android", "Debugging", 100, false)
        val download = LiveActivitySource("android", "download", "Android", "Download", 200)
        val history = rememberLiveActivitySource(listOf(debugging), download)
        assertEquals(listOf(download, debugging), history)
        assertTrue(history.first().enabled)
        assertFalse(history.last().enabled)
    }

    @Test fun historyCapsEnabledSourcesAndKeepsEveryDisabledPreference() {
        val disabled = (0 until 150).map { LiveActivitySource("app", "disabled$it", "App", "", it.toLong(), false) }
        var history = disabled
        repeat(200) { index ->
            history = rememberLiveActivitySource(history, LiveActivitySource("app", "enabled$index", "App", "", index.toLong()))
        }
        assertEquals(128, history.count { it.enabled })
        assertEquals(disabled, history.filterNot { it.enabled })
        assertEquals("enabled199", history.first().channelId)
        assertFalse(history.any { it.channelId == "enabled0" })
    }

    @Test fun sourceKeysDoNotCollideAcrossPackagesOrDelimiterCharacters() {
        assertNotEquals(liveActivitySourceKey("a", "bc"), liveActivitySourceKey("ab", "c"))
        assertNotEquals(liveActivitySourceKey("app.one", "x|y"), liveActivitySourceKey("app.two", "x|y"))
    }
}
