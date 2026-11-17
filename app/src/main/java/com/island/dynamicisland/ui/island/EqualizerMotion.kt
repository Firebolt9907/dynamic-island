package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.tween
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import kotlin.coroutines.coroutineContext

private val equalizerPeriods = intArrayOf(520, 700, 610, 470, 560, 650)

/** Wait without requesting frames when motion is disabled; keep the same level instances. */
internal suspend fun animateEqualizer(
    levels: List<Animatable<Float, AnimationVector1D>>,
    playing: Boolean,
    animationsEnabled: Boolean = true,
) {
    val durationScale = coroutineContext[MotionDurationScale]
    snapshotFlow { durationScale?.scaleFactor ?: 1f }.collectLatest { scale ->
        coroutineScope {
            levels.forEachIndexed { index, level ->
                launch {
                    when {
                        scale <= 0f || !animationsEnabled -> level.snapTo(0.30f)
                        !playing -> level.animateTo(0.30f, tween(260))
                        else -> while (true) {
                            // Still yields if the OS changes duration scale between emissions.
                            withFrameNanos { }
                            val period = equalizerPeriods[index % equalizerPeriods.size]
                            level.animateTo(1f - index * 0.035f, tween(period))
                            level.animateTo(0.22f + index * 0.025f, tween(period))
                        }
                    }
                }
            }
        }
    }
}

/**
 * On-screen easing between 20 Hz captures (50 ms apart). Rises reach ~90% of a new level within
 * one capture so hits land on the beat; falls lose ~38% per capture, just enough to bridge
 * captures without strobing.
 */
private const val LiveAttackMs = 22f
private const val LiveReleaseMs = 104f

/**
 * Captures arrive at 20 Hz, so redrawing at a 120 Hz panel's full rate only burns power;
 * ~60 fps still gives three smoothed steps per capture. Waiting out the gap (rather than
 * skipping vsyncs inside withFrameNanos) means the skipped frames are never requested.
 */
private const val LiveFrameIntervalMs = 15L

/** Show live bars only while captures carry sound; muted output or Cast reads as digital silence. */
private const val LiveSignalTimeoutMs = 1_500L

/**
 * After play, bars rest at the bottom (rather than flashing synthetic motion) while the player
 * restarts its stream and [com.island.dynamicisland.audio.LiveSpectrum] reattaches.
 */
private const val LiveStartGraceMs = 2_500L

/**
 * Smoothed live bar heights, bass (index 0) to treble. [frame] advances whenever [values]
 * change so a draw pass that reads it is invalidated without recomposing.
 */
internal class LiveBars(val count: Int) {
    val values = FloatArray(count)
    val frame = androidx.compose.runtime.mutableIntStateOf(0)
    var showing by androidx.compose.runtime.mutableStateOf(false)

    /** Ease toward [targets] (one per spectrum band) over [dtMs]; returns whether anything moved. */
    fun step(targets: FloatArray, dtMs: Float): Boolean {
        var moved = false
        for (i in 0 until count) {
            // Six bars map one-to-one; other counts sample bands left to right, keeping bass left.
            val band = if (count == targets.size) i else (i * targets.size / count).coerceAtMost(targets.size - 1)
            val target = targets[band]
            val current = values[i]
            val tau = if (target > current) LiveAttackMs else LiveReleaseMs
            val next = current + (target - current) * (1f - kotlin.math.exp(-dtMs / tau))
            if (kotlin.math.abs(next - current) > 0.001f) moved = true
            values[i] = next
        }
        return moved
    }
}

/**
 * Live spectrum bars for a playing track, or null when live data is off for this composition
 * (no audio permission, Visualizer unavailable, or [enabled] false). Callers still check
 * [LiveBars.showing]: it drops while output is silent or system animations are disabled.
 */
@androidx.compose.runtime.Composable
internal fun rememberLiveBars(enabled: Boolean, count: Int): LiveBars? {
    val context = androidx.compose.ui.platform.LocalContext.current
    val bars = androidx.compose.runtime.remember(count) { LiveBars(count) }
    var acquired by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.DisposableEffect(enabled, context) {
        val ok = enabled && com.island.dynamicisland.audio.LiveSpectrum.acquire(context)
        acquired = ok
        onDispose {
            if (ok) com.island.dynamicisland.audio.LiveSpectrum.release()
            acquired = false
            bars.showing = false
        }
    }
    androidx.compose.runtime.LaunchedEffect(acquired, bars) {
        if (!acquired) return@LaunchedEffect
        val durationScale = coroutineContext[MotionDurationScale]
        val startedAt = android.os.SystemClock.uptimeMillis()
        snapshotFlow { (durationScale?.scaleFactor ?: 1f) > 0f }.collectLatest { motion ->
            // With system animations off, request no frames at all; the static fallback shows.
            if (!motion) {
                bars.showing = false
                return@collectLatest
            }
            var last = 0L
            while (true) {
                if (last != 0L) {
                    val wait = LiveFrameIntervalMs - (System.nanoTime() - last) / 1_000_000L
                    if (wait > 0) kotlinx.coroutines.delay(wait)
                }
                withFrameNanos { now ->
                    val dt = if (last == 0L) 16f else ((now - last) / 1_000_000f).coerceIn(0f, 100f)
                    last = now
                    com.island.dynamicisland.audio.LiveSpectrum.maintain()
                    val clock = android.os.SystemClock.uptimeMillis()
                    val heardAt = com.island.dynamicisland.audio.LiveSpectrum.lastAudibleAtMs
                    val show = (heardAt > 0L && clock - heardAt < LiveSignalTimeoutMs) ||
                        (heardAt == 0L && clock - startedAt < LiveStartGraceMs)
                    if (bars.showing != show) {
                        bars.showing = show
                        android.util.Log.d("LiveSpectrum", if (show) "bars: live" else "bars: synthetic fallback")
                    }
                    if (show && bars.step(com.island.dynamicisland.audio.LiveSpectrum.targets, dt)) bars.frame.intValue++
                }
            }
        }
    }
    return if (acquired) bars else null
}
