package com.island.dynamicisland.ui.island

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/** Brief, discrete feedback for island gestures, with no ramp or trailing vibration. */
class IslandHaptics(private val vibrator: Vibrator?) {
    fun press() = tick(0.35f)

    fun expand() = tick(0.7f)

    fun collapse() = tick(0.5f)

    fun tap() = tick(0.6f)

    /** A quiet rising double tick, synchronized with a scoreboard celebration. */
    fun score() {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val tick = VibrationEffect.Composition.PRIMITIVE_TICK
        val effect = if (v.areAllPrimitivesSupported(tick)) {
            VibrationEffect.startComposition()
                .addPrimitive(tick, .20f)
                .addPrimitive(tick, .32f, 60)
                .compose()
        } else VibrationEffect.createWaveform(longArrayOf(0, 7, 60, 9), intArrayOf(0, 40, 0, 60), -1)
        v.vibrate(effect)
    }

    private fun tick(strength: Float) {
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val primitive = VibrationEffect.Composition.PRIMITIVE_TICK
        val effect = if (v.areAllPrimitivesSupported(primitive)) {
            VibrationEffect.startComposition()
                .addPrimitive(primitive, strength)
                .compose()
        } else {
            VibrationEffect.createOneShot(8, (strength * 180).toInt())
        }
        v.vibrate(effect)
    }
}

/** Remember an [IslandHaptics] bound to the device vibrator for the current composition. */
@Composable
fun rememberIslandHaptics(): IslandHaptics {
    val context = LocalContext.current
    return remember(context) { IslandHaptics(context.systemVibrator()) }
}

private fun Context.systemVibrator(): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
