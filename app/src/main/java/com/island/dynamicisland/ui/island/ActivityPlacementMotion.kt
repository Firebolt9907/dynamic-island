package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch

/** Deliberate acceleration and a long settling tail for detached activity travel. */
internal object ActivityBubbleMotion {
    private val travelEasing = CubicBezierEasing(.42f, 0f, .18f, 1f)
    val arrival = tween<Float>(620, easing = travelEasing)
    val merge = tween<Float>(380, easing = travelEasing)
    val departure = tween<Float>(460, easing = travelEasing)

    /** Scale a bubble (or its content) has while fully faded out. */
    const val FADED_SCALE = .7f

    /** Scale paired with a fade, so entering bubbles grow from 70% as they appear and leaving ones shrink back. */
    fun fadeScale(alpha: Float): Float = FADED_SCALE + (1f - FADED_SCALE) * alpha.coerceIn(0f, 1f)
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

/**
 * Membership changes can reflow through the camera instead of gliding between slots:
 * every activity is absorbed into the lens, the layout swaps while nothing is visible,
 * and the new set emerges. One absorption value drives both directions, so an
 * interrupted collapse or emergence reverses from exactly where it is.
 */
internal class CameraReflowMotion {
    /**
     * 0 = every activity at its slot, 1 = every activity inside the camera. The entrance
     * spring briefly overshoots below 0, stretching the activities past their slots.
     */
    val absorption = Animatable(0f)
    var running by mutableStateOf(false)
        private set
    /** Keys that trail the primary, so side bubbles are absorbed first and emerge last. */
    private var trailing by mutableStateOf(emptySet<String>())
    /** Cancellation discards an Animatable's velocity; keep it for the reversing spring. */
    private var handoffVelocity = 0f

    /** Every activity is invisible from here on; the layout may be swapped. */
    val hidden get() = absorption.value >= HIDDEN
    val active get() = running || absorption.value != 0f
    /** True while the new layout grows out of the lens, until it settles or is reversed. */
    var emerging by mutableStateOf(false)
        private set
    /**
     * Touches may land once an entrance has mostly grown out; waiting for the overshoot to
     * settle makes rapid taps miss. An absorption in progress is never pressable.
     */
    val pressable get() = !active || (emerging && absorption.value <= PRESSABLE)

    /** How far [key] has been drawn into the camera. */
    fun absorption(key: String?): Float {
        val a = absorption.value
        return if (key in trailing) minOf(1f, a / (1f - TRAIL)) else a
    }

    /** Time-based content opacity, so the fade reads even while the springs move quickly. */
    val contentVisibility = Animatable(1f)

    /**
     * Opacity of what is drawn inside [key]'s silhouette. A timed fade leads the shrink on
     * the way in and accompanies the growth on the way out; a geometric gate additionally
     * keeps content off a bubble small enough to clip it, and holds trailing bubbles back.
     */
    fun contentAlpha(key: String?): Float {
        val p = ((absorption(key) - CONTENT_GATE_START) / (CONTENT_GATE_END - CONTENT_GATE_START)).coerceIn(0f, 1f)
        return contentVisibility.value * (1f - p * p * (3f - 2f * p))
    }

    /**
     * Fade the content, then pull everything into the camera. Like an interruptible iOS
     * spring, a reversal keeps the current velocity. Returns once nothing is visible,
     * without waiting for the spring's asymptotic tail inside the lens.
     */
    suspend fun collapse(trailingKeys: Set<String>) = coroutineScope {
        val fresh = !running && absorption.value == 0f
        running = true
        emerging = false
        trailing = trailingKeys
        launch { contentVisibility.animateTo(0f, ActivityReflowSpecs.contentOut) }
        if (hidden) return@coroutineScope
        // Let the fade get going before the silhouette starts closing over it.
        if (fresh) awaitFrameTime(ActivityReflowSpecs.CONTENT_LEAD_MS)
        try {
            absorption.animateTo(1f, ActivityReflowSpecs.collapse, handoffVelocity) {
                handoffVelocity = velocity
                if (value >= HIDDEN) throw ReachedHidden()
            }
        } catch (_: ReachedHidden) {
            currentCoroutineContext().ensureActive()
        }
        // The new layout starts from rest rather than carrying inward momentum.
        handoffVelocity = 0f
    }

    /** The new layout grows out of the lens from rest, overshoots slightly, and settles. */
    suspend fun emerge(trailingKeys: Set<String>) {
        trailing = trailingKeys
        emerging = true
        try {
            coroutineScope {
                // No extra wait: the geometric gate already withholds content until the
                // bubble is large enough not to clip it.
                launch { contentVisibility.animateTo(1f, ActivityReflowSpecs.contentIn) }
                absorption.animateTo(0f, ActivityReflowSpecs.emergence, handoffVelocity) { handoffVelocity = velocity }
                handoffVelocity = 0f
            }
        } finally {
            emerging = false
            if (absorption.value == 0f && contentVisibility.value == 1f) running = false
        }
    }

    /** Start already inside the lens, e.g. after something else carried the island there. */
    suspend fun hide() {
        running = true
        emerging = false
        handoffVelocity = 0f
        absorption.snapTo(1f)
        contentVisibility.snapTo(0f)
    }

    suspend fun reset() {
        absorption.snapTo(0f)
        contentVisibility.snapTo(1f)
        handoffVelocity = 0f
        emerging = false
        running = false
    }

    internal companion object {
        /** Trailing activities reach the lens this much earlier and leave it this much later. */
        const val TRAIL = .16f
        const val HIDDEN = .92f
        /** Below this absorption an emerging layout is large enough to take a tap. */
        const val PRESSABLE = .35f
        /** Content is withheld across this absorption range, well before the silhouette fades. */
        const val CONTENT_GATE_START = .15f
        const val CONTENT_GATE_END = .55f
    }
}

/** Wait on the animation frame clock, keeping phase offsets in step with the springs. */
private suspend fun awaitFrameTime(millis: Long) {
    val start = withFrameNanos { it }
    while (withFrameNanos { it } - start < millis * 1_000_000L) Unit
}

/**
 * Ends the collapse spring once nothing is visible. Animatable only resets its running
 * state for cancellations, so this is one; the caller re-checks its own cancellation.
 */
private class ReachedHidden : kotlinx.coroutines.CancellationException("reflow hidden")

/**
 * SwiftUI-style springs, converted with stiffness = (2π / response)² for unit mass.
 * iOS animates island geometry with springs rather than fixed curves: the collapse is
 * critically damped, the entrance has a small bounce.
 */
internal object ActivityReflowSpecs {
    private fun stiffness(responseSeconds: Float): Float =
        (2f * kotlin.math.PI.toFloat() / responseSeconds).let { it * it }

    /** response .32 s, no bounce. */
    val collapse = spring(dampingRatio = 1f, stiffness = stiffness(.32f), visibilityThreshold = .001f)
    /** Content leads the collapse and trails the entrance, slow enough to read as a fade. */
    const val CONTENT_LEAD_MS = 90L
    val contentOut = tween<Float>(200, easing = LinearOutSlowInEasing)
    /** Starts with the growth and is nearly opaque as the entrance spring reaches the slot. */
    val contentIn = tween<Float>(280, easing = LinearOutSlowInEasing)

    /** response .5 s, damping .7: roughly a 4-5% overshoot past the slot. */
    val emergence = spring(dampingRatio = .7f, stiffness = stiffness(.5f), visibilityThreshold = .001f)
}

/** An activity drawn [absorption] of the way into a camera lens of [lensWidth]. */
internal fun ActivityPlacement.absorbed(cameraCenter: Float, lensWidth: Float, absorption: Float): ActivityPlacement {
    val a = absorption.coerceAtMost(1f)
    if (a == 0f) return this
    // Negative absorption is the entrance overshoot: geometry stretches past the slot,
    // while opacity and content detail simply stay full.
    fun mix(from: Float, to: Float) = from + (to - from) * a
    val inward = a.coerceAtLeast(0f)
    // Content gives way first, the black silhouette disappears only once it is lens-sized.
    val detailFade = (1f - inward / .45f).coerceIn(0f, 1f)
    val vanish = ((inward - .62f) / (CameraReflowMotion.HIDDEN - .62f)).coerceIn(0f, 1f)
    return ActivityPlacement(mix(center, cameraCenter), mix(width, minOf(width, lensWidth)),
        alpha * (1f - vanish * vanish * (3f - 2f * vanish)), detail * detailFade, grouping * detailFade)
}

/** Stable activity identities retain their on-screen position when a slot assignment changes. */
internal class ActivityPlacementMotion(initial: Map<String, ActivityPlacement>, private val lensWidth: Float,
                                       private val trace: (String) -> Unit = {},
                                       private val reflow: CameraReflowMotion? = null) {
    val progress = Animatable(1f)
    private var origins by mutableStateOf(initial)
    private var destinations by mutableStateOf(initial)
    var cameraCenter by mutableStateOf(0f)
    val destinationKeys: Set<String> get() = destinations.keys

    fun frame(key: String?, fallback: ActivityPlacement): ActivityPlacement {
        if (key == null) return fallback
        val destination = destinations[key] ?: return arrival()
        val placed = (origins[key] ?: arrival()).towards(destination, progress.value)
        return reflow?.let { placed.absorbed(cameraCenter, lensWidth, it.absorption(key)) } ?: placed
    }

    /**
     * How much the entrance overshoot has stretched [key] past its slot, so its content can
     * swell with it. Exactly 1 at rest, while collapsing, and during ordinary slot travel.
     */
    fun overshootScale(key: String?): Float {
        val absorption = reflow?.absorption(key) ?: return 1f
        if (key == null || absorption >= 0f) return 1f
        val destination = destinations[key] ?: return 1f
        val placed = (origins[key] ?: arrival()).towards(destination, progress.value)
        if (placed.width <= 0f) return 1f
        return (placed.absorbed(cameraCenter, lensWidth, absorption).width / placed.width).coerceAtLeast(1f)
    }

    /** Jump to [next] while it cannot be seen, e.g. inside the camera during a reflow. */
    suspend fun snap(next: Map<String, ActivityPlacement>) {
        progress.snapTo(1f)
        origins = next
        destinations = next
        trace("placement snap count=${next.size}")
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

    private fun arrival() = ActivityPlacement(cameraCenter, lensWidth, 0f, 0f)

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
                                secondaryLeadingWidth: Float = bubbleSize * .6f,
                                secondaryWidths: Map<String, Float> = emptyMap()): Map<String, ActivityPlacement> = buildMap {
    slots.primary?.let { put(it, ActivityPlacement(primaryCenter, primaryWidth, 1f, 1f, if (slots.secondaries.size == 1) 1f else 0f)) }
    slots.secondaries.forEachIndexed { index, key ->
        val detailed = slots.secondaries.size == 1
        val width = secondaryWidths[key] ?: if (detailed) compactActivityPillWidth(bubbleSize, secondaryIndicatorWidth, secondaryLeadingWidth) else bubbleSize
        val position = activityBubblePosition(index, primaryCenter - primaryWidth / 2f, primaryWidth, width)
        put(key, ActivityPlacement(position.left + width / 2f, width, 1f, if (detailed) 1f else 0f))
    }
}

internal fun compactMusicPillWidth(height: Float): Float = height * (0.4f + CompactArtworkLeadingInset + CompactArtworkTrailingGap + CompactArtworkFraction + CompactVisualizerWidthFraction + CompactVisualizerTrailingInset) + 6f

internal fun compactActivityPillWidth(height: Float, indicatorWidth: Float,
                                      leadingWidth: Float = height * .6f): Float =
    if (indicatorWidth > 0f && leadingWidth <= 0f) maxOf(height, height * .4f + indicatorWidth) // No leading icon, so no gap.
    else if (indicatorWidth > 0f) height * .4f + leadingWidth + 6f + indicatorWidth else height

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
