package com.island.dynamicisland.power

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

internal val LocalPowerSaveMode = staticCompositionLocalOf { false }

/** One event-driven observer per island composition; no battery-state polling. */
@Composable
internal fun rememberPowerSaveMode(): State<Boolean> {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val enabled = remember(power) { mutableStateOf(power.isPowerSaveMode) }
    DisposableEffect(context, power) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                enabled.value = power.isPowerSaveMode
            }
        }
        ContextCompat.registerReceiver(context, receiver,
            IntentFilter(PowerManager.ACTION_POWER_SAVE_MODE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED)
        // Close the gap between the initial read and registering the receiver.
        enabled.value = power.isPowerSaveMode
        onDispose { context.unregisterReceiver(receiver) }
    }
    return enabled
}
