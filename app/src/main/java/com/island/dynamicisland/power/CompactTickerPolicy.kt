package com.island.dynamicisland.power

import android.content.Context
import android.os.PowerManager
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

internal val LocalCompactTickerAllowed = staticCompositionLocalOf { true }
internal val LocalInteractionMotionAllowed = staticCompositionLocalOf { true }

/** Thermals may suppress compact bubble motion, never expanded interaction feedback. */
internal fun allowIslandMotion(powerSave: Boolean, thermalStatus: Int, compact: Boolean): Boolean =
    !powerSave && (!compact || thermalStatus == PowerManager.THERMAL_STATUS_NONE)

internal fun allowInteractionMotion(powerSave: Boolean, thermalStatus: Int): Boolean =
    allowIslandMotion(powerSave, thermalStatus, compact = false)

internal fun allowCompactTicker(powerSave: Boolean, thermalStatus: Int): Boolean =
    allowIslandMotion(powerSave, thermalStatus, compact = true)

/** A single thermal callback for the island; no temperature or battery polling. */
@Composable
internal fun rememberThermalStatus(): State<Int> {
    val context = LocalContext.current
    val power = remember(context) { context.getSystemService(PowerManager::class.java) }
    val status = remember(power) { mutableIntStateOf(power.currentThermalStatus) }
    DisposableEffect(power) {
        val listener = PowerManager.OnThermalStatusChangedListener { status.intValue = it }
        power.addThermalStatusListener(context.mainExecutor, listener)
        status.intValue = power.currentThermalStatus
        onDispose { power.removeThermalStatusListener(listener) }
    }
    return status
}
