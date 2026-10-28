package com.island.dynamicisland.audio

import org.junit.Assert.assertEquals
import org.junit.Test

class BluetoothAudioMonitorTest {
    @Test fun reportsConnectionsAndRemembersDisconnectedNames() {
        assertEquals(listOf("Headphones" to true), bluetoothAudioChanges(emptyMap(), mapOf("a" to "Headphones")))
        assertEquals(listOf("Headphones" to false), bluetoothAudioChanges(mapOf("a" to "Headphones"), emptyMap()))
    }
    @Test fun stableDeviceAndNameChangesDoNotRepeatConnection() {
        assertEquals(emptyList<Pair<String, Boolean>>(), bluetoothAudioChanges(mapOf("a" to "Headphones"), mapOf("a" to "Renamed")))
    }
    @Test fun switchingDevicesReportsBothTransitions() {
        assertEquals(listOf("Headphones" to false, "Speaker" to true),
            bluetoothAudioChanges(mapOf("a" to "Headphones"), mapOf("b" to "Speaker")))
    }
}
