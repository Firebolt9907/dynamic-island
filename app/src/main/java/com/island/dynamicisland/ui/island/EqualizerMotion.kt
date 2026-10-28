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
import kotlin.coroutines.coroutineContext

private val equalizerPeriods = intArrayOf(520, 700, 610, 470, 560)

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
