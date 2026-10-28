package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Deliberate acceleration and a long settling tail for detached activity travel. */
internal object ActivityBubbleMotion {
    private val travelEasing = CubicBezierEasing(.42f, 0f, .18f, 1f)
    val arrival = tween<Float>(620, easing = travelEasing)
    val merge = tween<Float>(380, easing = travelEasing)
    val departure = tween<Float>(460, easing = travelEasing)
}

internal data class ActivityPlacement(val center: Float, val width: Float, val alpha: Float, val detail: Float, val grouping: Float = 0f) {
    fun towards(other: ActivityPlacement, progress: Float): ActivityPlacement {
        val p = progress.coerceIn(0f, 1f)
        if (p == 0f) return this
        if (p == 1f) return other
        fun mix(a: Float, b: Float) = a + (b - a) * p
        return ActivityPlacement(mix(center, other.center), mix(width, other.width),
            mix(alpha, other.alpha), mix(detail, other.detail), mix(grouping, other.grouping))
    }
}

/** Stable activity identities retain their on-screen position when a slot assignment changes. */
internal class ActivityPlacementMotion(initial: Map<String, ActivityPlacement>, private val lensWidth: Float,
                                       private val trace: (String) -> Unit = {}) {
    val progress = Animatable(1f)
    private var origins by mutableStateOf(initial)
    private var destinations by mutableStateOf(initial)

    fun frame(key: String?, fallback: ActivityPlacement): ActivityPlacement {
        if (key == null) return fallback
        val destination = destinations[key] ?: return arrival()
        return (origins[key] ?: arrival()).towards(destination, progress.value)
    }

    /** A stationary native input window covers the visual's complete compact travel. */
    fun touchEnvelope(key: String?, fallback: ActivityPlacement): ActivityPlacement {
        val destination = destinations[key] ?: fallback
        if (progress.value == 1f) return destination
        val origin = origins[key] ?: arrival()
        val left = minOf(origin.center - origin.width / 2f, destination.center - destination.width / 2f)
        val right = maxOf(origin.center + origin.width / 2f, destination.center + destination.width / 2f)
        return destination.copy(center = (left + right) / 2f, width = right - left)
    }

    private fun arrival() = ActivityPlacement(0f, lensWidth, 0f, 0f)

    suspend fun retarget(next: Map<String, ActivityPlacement>) {
        if (next == destinations) return
        // Sample before resetting progress so a second arrival doesn't restart the first jump.
        val current = next.mapValues { (key, target) -> frame(key, target) }
        progress.snapTo(0f)
        origins = current
        destinations = next
        val started = System.nanoTime()
        var lastFrame = started
        var longestGap = 0L
        var frames = 0
        var completed = false
        trace("placement start count=${next.size} origins=${current.values} targets=${next.values}")
        try {
            progress.animateTo(1f, ActivityBubbleMotion.arrival) {
                val now = System.nanoTime()
                longestGap = maxOf(longestGap, now - lastFrame)
                lastFrame = now
                frames++
            }
            completed = true
        } finally {
            trace("placement end completed=$completed progress=${progress.value} frames=$frames maxGapMs=${longestGap / 1_000_000} durationMs=${(System.nanoTime() - started) / 1_000_000}")
        }
    }
}

internal fun activityPlacements(slots: IslandActivities, primaryWidth: Float, bubbleSize: Float,
                                primaryCenter: Float = 0f, secondaryIndicatorWidth: Float = bubbleSize * .54f,
                                secondaryLeadingWidth: Float = bubbleSize * .6f): Map<String, ActivityPlacement> = buildMap {
    slots.primary?.let { put(it, ActivityPlacement(primaryCenter, primaryWidth, 1f, 1f, if (slots.secondaries.size == 1) 1f else 0f)) }
    slots.secondaries.forEachIndexed { index, key ->
        val detailed = slots.secondaries.size == 1
        val width = if (detailed) compactActivityPillWidth(bubbleSize, secondaryIndicatorWidth, secondaryLeadingWidth) else bubbleSize
        val position = activityBubblePosition(index, primaryCenter - primaryWidth / 2f, primaryWidth, width)
        put(key, ActivityPlacement(position.left + width / 2f, width, 1f, if (detailed) 1f else 0f))
    }
}

internal fun compactMusicPillWidth(height: Float): Float = height * (0.4f + 0.6f + 0.54f) + 6f

internal fun compactActivityPillWidth(height: Float, indicatorWidth: Float,
                                      leadingWidth: Float = height * .6f): Float =
    if (indicatorWidth > 0f) height * .4f + leadingWidth + 6f + indicatorWidth else height

/** Crossfade late in the merge, with zero endpoint velocity on opening and reversal. */
internal fun activityHandoffFraction(transfer: Float): Float {
    val p = ((transfer - .55f) / .45f).coerceIn(0f, 1f)
    return p * p * (3f - 2f * p)
}

/** Companions converge on the selected activity; they never become the expanding card. */
internal fun mergingActivityPlacement(origin: ActivityPlacement, selected: ActivityPlacement,
                                      transfer: Float): ActivityPlacement =
    origin.towards(origin.copy(center = selected.center), transfer)

/** Fully absorbed companions emerge from the lens on a secondary card's dismissal. */
internal fun returningActivityPlacement(destination: ActivityPlacement, cameraCenter: Float,
                                        lensWidth: Float, transfer: Float): ActivityPlacement =
    destination.towards(destination.copy(center = cameraCenter, width = lensWidth), transfer)
