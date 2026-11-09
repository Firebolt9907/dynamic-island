package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.collect
import com.island.dynamicisland.power.LocalInteractionMotionAllowed
import com.island.dynamicisland.power.LocalPowerSaveMode

internal fun normalizedLiveProgress(value: Float): Float =
    if (value.isFinite()) value.coerceIn(0f, 1f) else 0f

/** Give the original digit roll time to finish; retain only the newest pending percentage. */
@Composable
internal fun rememberTransferPercentage(progress: Float, active: Boolean): State<Int> {
    val latest = rememberUpdatedState((normalizedLiveProgress(progress) * 100).toInt())
    val displayed = remember { mutableStateOf(latest.value) }
    val animate = active && !LocalPowerSaveMode.current && LocalInteractionMotionAllowed.current
    LaunchedEffect(animate) {
        snapshotFlow { latest.value }.conflate().collect { percentage ->
            displayed.value = percentage
            if (animate) delay(500)
        }
    }
    return displayed
}

/** One interruptible, bounded spring; hidden content goes straight to the latest value. */
internal class LiveProgressMotion(initial: Float) {
    val progress = Animatable(normalizedLiveProgress(initial)).apply { updateBounds(0f, 1f) }

    suspend fun update(target: Float, animate: Boolean) {
        val next = normalizedLiveProgress(target)
        if (animate) progress.animateTo(next, spring(
            dampingRatio = 1f, stiffness = 100f, visibilityThreshold = .0001f,
        ))
        else progress.snapTo(next)
    }
}

@Composable
internal fun rememberLiveProgress(progress: Float, active: Boolean): State<Float> {
    val motion = remember { LiveProgressMotion(progress) }
    val animate = active && !LocalPowerSaveMode.current && LocalInteractionMotionAllowed.current
    val target = normalizedLiveProgress(progress)
    LaunchedEffect(target, animate) { motion.update(target, animate) }
    return motion.progress.asState()
}
