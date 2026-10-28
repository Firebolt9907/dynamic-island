package com.island.dynamicisland.ui.island

import org.junit.Assert.*
import org.junit.Test

class MusicSourceVisibilityTest {
    @Test fun hidesOnlyTheActiveMusicApp() {
        assertTrue(isMusicSourceOpen("music.app", "music.app"))
        assertFalse(isMusicSourceOpen("launcher", "music.app"))
        assertFalse(isMusicSourceOpen("other.music.app", "music.app"))
        assertFalse(isMusicSourceOpen(null, "music.app"))
        assertFalse(isMusicSourceOpen(null, null))
        assertFalse(isMusicSourceOpen("", ""))
    }
    @Test fun suppressingMusicPreservesLiveActivities() {
        val slots = IslandActivities.select(listOf("delivery", "timer"),
            !isMusicSourceOpen("music.app", "music.app"))
        assertEquals("delivery", slots.primary)
        assertEquals(listOf("timer"), slots.secondaries)
        assertNull(IslandActivities.select(emptyList(), false).primary)
    }
    @Test fun liveActivitiesAreHiddenOnlyWhileTheirOwnAppIsOpen() {
        val maps = com.island.dynamicisland.live.LiveUpdate("maps", "Maps", "Turn right", "", "", "",
            null, null, false, null, emptyList(), packageName = "com.google.android.apps.maps", isNavigation = true)
        assertTrue(isLiveActivitySourceOpen("com.google.android.apps.maps", maps))
        assertFalse(isLiveActivitySourceOpen("launcher", maps))
        assertFalse(isLiveActivitySourceOpen(null, maps))
        assertFalse(isLiveActivitySourceOpen("com.google.android.apps.maps", maps.copy(packageName = "delivery.app", isNavigation = false)))
        val delivery = maps.copy(packageName = "delivery.app", isNavigation = false)
        assertTrue(isLiveActivitySourceOpen("delivery.app", delivery))
        assertFalse(isLiveActivitySourceOpen("launcher", delivery))
        assertFalse(isLiveActivitySourceOpen("other.app", delivery))
        assertFalse(isLiveActivitySourceOpen("", maps.copy(packageName = "")))
    }
    @Test fun leavingSourceRestoresActivityWithoutRemovingOrHidingOtherSources() {
        val delivery = com.island.dynamicisland.live.LiveUpdate("delivery", "Delivery", "Arriving", "", "", "",
            null, null, false, null, emptyList(), packageName = "delivery.app")
        val timer = delivery.copy(key = "timer", packageName = "clock.app")
        val active = listOf(delivery, timer)
        fun visible(foreground: String?) = active.filterNot { isLiveActivitySourceOpen(foreground, it) }
        assertEquals(listOf(timer), visible("delivery.app"))
        assertEquals(active, visible("launcher"))
        assertEquals(listOf(delivery), visible("clock.app"))
        assertEquals(active, visible(null))
        assertEquals(listOf(timer), visible("delivery.app"))
    }

}
