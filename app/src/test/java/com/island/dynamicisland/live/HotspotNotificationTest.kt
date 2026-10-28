package com.island.dynamicisland.live

import org.junit.Assert.*
import org.junit.Test

class HotspotNotificationTest {
    @Test fun recognizesActiveSystemHotspotHeadings() {
        listOf("Hotspot is on", "Mobile Hotspot active", "Portable Wi-Fi hotspot is on",
            "Wi‑Fi hotspot enabled", "Mobile Hotspot", "Tethering or hotspot active").forEach {
            assertTrue(it, isHotspotNotification("android", listOf(it)))
            assertTrue(it, isHotspotNotification("com.google.android.networkstack.tethering", listOf(it)))
        }
    }

    @Test fun rejectsOtherAppsAndNonActiveSystemNotices() {
        assertFalse(isHotspotNotification("other.app", listOf("Hotspot is on")))
        listOf("Hotspot is off", "Hotspot disabled", "Hotspot is on: error", "Hotspot settings",
            "Hotspot available", "Turn on mobile hotspot", "USB tethering active",
            "Bluetooth tethering active", "Hotspot turned off", "Hotspot failed to start").forEach {
            assertFalse(it, isHotspotNotification("android", listOf(it)))
        }
    }

    @Test fun compactStatusUsesExplicitClientCountsOnly() {
        assertEquals("0 devices", hotspotCompactStatus(listOf("No connected devices")))
        assertEquals("0 devices", hotspotCompactStatus(listOf("No devices connected")))
        assertEquals("1 device", hotspotCompactStatus(listOf("1 device connected")))
        assertEquals("3 devices", hotspotCompactStatus(listOf("3 connected devices")))
        assertEquals("2 devices", hotspotCompactStatus(listOf("2 clients connected")))
        assertEquals("On", hotspotCompactStatus(listOf("Using 5 GHz", "250 MB used")))
    }
}
