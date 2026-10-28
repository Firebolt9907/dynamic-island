package com.island.dynamicisland.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.Display
import androidx.core.content.ContextCompat

/** Event-driven: AOD may keep the display powered while the device is non-interactive. */
internal class InteractiveDisplayMonitor(
    private val context: Context,
    private val onChanged: (Boolean) -> Unit,
) {
    private val power by lazy { context.getSystemService(PowerManager::class.java) }
    private val displays by lazy { context.getSystemService(DisplayManager::class.java) }
    private var started = false
    private var lastValue: Boolean? = null
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = refresh()
    }
    private val listener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = refresh()
        override fun onDisplayRemoved(displayId: Int) = refresh()
        override fun onDisplayChanged(displayId: Int) = refresh()
    }

    fun start() {
        if (started) return
        started = true
        ContextCompat.registerReceiver(context, receiver, IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }, ContextCompat.RECEIVER_NOT_EXPORTED)
        displays.registerDisplayListener(listener, Handler(Looper.getMainLooper()))
        refresh()
    }

    private fun refresh() {
        if (!started) return
        val active = power.isInteractive &&
            displays.getDisplay(Display.DEFAULT_DISPLAY)?.state == Display.STATE_ON
        if (lastValue != active) {
            lastValue = active
            onChanged(active)
        }
    }

    fun stop() {
        if (!started) return
        started = false
        context.unregisterReceiver(receiver)
        displays.unregisterDisplayListener(listener)
        lastValue = null
    }
}
