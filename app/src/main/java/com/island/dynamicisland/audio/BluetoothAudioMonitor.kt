package com.island.dynamicisland.audio

import android.Manifest
import android.bluetooth.BluetoothA2dp
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothLeAudio
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BluetoothAudioEvent(val name: String, val connected: Boolean, val sequence: Long, val batteryLevel: Int? = null, val deviceKey: String = "")

/** Observe audio outputs rather than ACL connections (which also include watches/keyboards). */
class BluetoothAudioMonitor(private val context: Context, private val scope: CoroutineScope) {
    companion object {
        internal var active: BluetoothAudioMonitor? = null
            private set
    }

    internal fun showPreview(name: String, battery: Int) {
        if (context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        expiry?.cancel()
        state.value = BluetoothAudioEvent(name, true, ++sequence, battery.takeIf { it in 0..100 })
        expiry = scope.launch {
            delay(12000)
            state.value = null
        }
    }

    private val manager = context.getSystemService(AudioManager::class.java)
    private val state = MutableStateFlow<BluetoothAudioEvent?>(null)
    val event = state.asStateFlow()
    private var devices = emptyMap<String, String>()
    private var refresh: Job? = null
    private var expiry: Job? = null
    private var started = false
    private var sequence = 0L
    private val reported = mutableMapOf<String, Boolean>()
    private val profiles = mutableMapOf<String, MutableSet<String>>()
    private val disconnects = mutableMapOf<String, Job>()
    private fun publishConnection(key: String, name: String, connected: Boolean) {
        if (reported[key] == connected || (!connected && reported[key] != true)) return
        reported[key] = connected
        expiry?.cancel()
        state.value = BluetoothAudioEvent(name, connected, ++sequence, batteries[key] ?: batteries[name], key)
        // The disconnect card owns this last reading; a future connection waits for fresh data.
        if (!connected) {
            batteries.remove(key)
            batteries.remove(name)
        }
        expiry = scope.launch {
            delay(3500)
            state.value = null
        }
    }
    private val batteries = mutableMapOf<String, Int>()
    private var batteryReceiverRegistered = false
    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) return
            @Suppress("DEPRECATION")
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val address = device.address
            val name = device.name.orEmpty().ifBlank { "Bluetooth audio" }
            if (intent.action != "android.bluetooth.device.action.BATTERY_LEVEL_CHANGED") {
                val action = intent.action ?: return
                when (intent.getIntExtra(BluetoothProfile.EXTRA_STATE, -1)) {
                    BluetoothProfile.STATE_CONNECTED -> {
                        disconnects.remove(address)?.cancel()
                        profiles.getOrPut(address) { mutableSetOf() }.add(action)
                        publishConnection(address, name, true)
                    }
                    BluetoothProfile.STATE_DISCONNECTED -> {
                        profiles[address]?.remove(action)
                        disconnects.remove(address)?.cancel()
                        disconnects[address] = scope.launch {
                            delay(750)
                            if (profiles[address].isNullOrEmpty() && address !in snapshot())
                                publishConnection(address, name, false)
                            disconnects.remove(address)
                        }
                    }
                }
                return
            }
            val level = intent.getIntExtra("android.bluetooth.device.extra.BATTERY_LEVEL", -1)
            // Android resets to UNKNOWN on disconnect. Keep the last real sample until
            // publishConnection has copied it into the disconnect card, in either event order.
            if (level !in 0..100) return
            batteries[address] = level
            if (name.isNotBlank()) batteries[name] = level
            state.value?.takeIf { it.deviceKey == address || it.name == name }?.let {
                state.value = it.copy(batteryLevel = level.takeIf { value -> value in 0..100 })
            }
        }
    }
    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = scheduleRefresh(120)
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = scheduleRefresh(750)
    }

    private fun snapshot(): Map<String, String> = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        .filter { it.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER) }
        .associate { device ->
            val name = device.productName?.toString()?.trim().orEmpty().ifBlank { "Bluetooth audio" }
            // A headset can expose both media and call endpoints. Collapse those into one device.
            device.address.ifBlank { name } to name
        }

    fun start() {
        if (started) return
        devices = snapshot()
        devices.keys.forEach { reported[it] = true }
        ContextCompat.registerReceiver(context, batteryReceiver,
            IntentFilter("android.bluetooth.device.action.BATTERY_LEVEL_CHANGED").apply {
                addAction(BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED)
                addAction(BluetoothLeAudio.ACTION_LE_AUDIO_CONNECTION_STATE_CHANGED)
            },
            ContextCompat.RECEIVER_EXPORTED)
        batteryReceiverRegistered = true
        started = true
        active = this
        manager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun scheduleRefresh(settleMs: Long) {
        if (!started) return
        refresh?.cancel()
        refresh = scope.launch {
            // Profile endpoints arrive independently; avoid duplicate banners and route-switch flashes.
            delay(settleMs)
            val next = snapshot()
            val changes = bluetoothAudioChanges(devices, next)
            val previous = devices
            devices = next
            for ((name, connected) in changes) {
                val key = (if (connected) next else previous).entries.firstOrNull { it.value == name }?.key.orEmpty()
                if (connected || profiles[key].isNullOrEmpty()) publishConnection(key, name, connected)
            }
        }
    }

    fun stop() {
        if (!started) return
        started = false
        if (active === this) active = null
        manager.unregisterAudioDeviceCallback(callback)
        if (batteryReceiverRegistered) context.unregisterReceiver(batteryReceiver)
        batteryReceiverRegistered = false
        batteries.clear()
        reported.clear()
        profiles.clear()
        disconnects.values.forEach { it.cancel() }
        disconnects.clear()
        refresh?.cancel()
        expiry?.cancel()
        state.value = null
        devices = emptyMap()
    }
}

internal fun bluetoothAudioChanges(previous: Map<String, String>, next: Map<String, String>): List<Pair<String, Boolean>> =
    previous.filterKeys { it !in next }.values.map { it to false } +
        next.filterKeys { it !in previous }.values.map { it to true }
