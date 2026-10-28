package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/** One interruptible physical trajectory; recognition never restarts the spring. */
internal object IslandGrowth {
    const val hapticProgress = 0.58f

    // Width leads height along a bowed path; reversal follows the identical silhouette.
    fun widthFraction(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p + .36f * p * (1f - p)
    }

    fun heightFraction(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return p - .24f * p * (1f - p)
    }

    fun durationForHold(longPressTimeoutMs: Long): Int =
        maxOf(700L, longPressTimeoutMs + 180L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()

    fun spec(fullDurationMs: Int): SpringSpec<Float> {
        // Inspired by Atoll's separate open/close spring responses. Critical damping
        // keeps the camera silhouette smooth without clipping an overshooting spring.
        // Scale the response with accessibility's hold timeout, never with distance:
        // a reversal must inherit its actual velocity rather than restart an easing curve.
        val responseSeconds = fullDurationMs / 1000f * 0.6f
        val angularFrequency = (2f * Math.PI.toFloat()) / responseSeconds
        return spring(dampingRatio = 1f, stiffness = angularFrequency * angularFrequency,
            visibilityThreshold = 0.001f)
    }
}
