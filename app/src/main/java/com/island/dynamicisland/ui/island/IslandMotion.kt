package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope

/** Compact press feedback precedes interruptible growth; recognition commits that growth. */
internal class IslandMotion(private val scope: CoroutineScope) {
    val progress = Animatable(0f)
    private var dragProgress by mutableStateOf<Float?>(null)
    val fraction: Float get() = dragProgress ?: progress.value
    val transfer = Animatable(0f)
    val pressScale = Animatable(1f)
    var returningFromCamera by mutableStateOf(false)
        private set
    private var scaleAnimation: Job? = null
    var secondary by mutableStateOf(false)
        private set
    var mergingCompanions by mutableStateOf(false)
        private set
    var mergingPair by mutableStateOf(false)
        private set
    val transferringActivities: Boolean get() = secondary || mergingCompanions
    var musicPeek by mutableStateOf(false)
        private set
    var automaticallyExpanded by mutableStateOf(false)
        private set
    var expanded by mutableStateOf(false)
        private set
    var previewActive by mutableStateOf(false)
        private set
    private var animation: Job? = null
    private var expansionAllowed = false
    private var pressActive = false
    private var beganExpanded = false

    enum class TapResult { OPEN_APP, KEEP_EXPANDED, COLLAPSED, IGNORED }

    /** Decide using the state at finger-down and visible growth, not just Android's timeout. */
    fun releaseTap(): TapResult {
        if (!pressActive) return TapResult.IGNORED
        pressActive = false
        return when {
            beganExpanded -> { dismiss(); TapResult.COLLAPSED }
            expansionAllowed && progress.value >= IslandGrowth.hapticProgress -> {
                commit()
                TapResult.KEEP_EXPANDED
            }
            else -> { tapRebound(); TapResult.OPEN_APP }
        }
    }

    fun recognizeLongPress(): Boolean {
        if (!pressActive) return false
        val collapse = beganExpanded
        pressActive = false
        if (collapse) dismiss() else commit()
        return collapse
    }

    fun press(hasMedia: Boolean, longPressTimeoutMs: Long, fromSecondary: Boolean = false, mergeCompanions: Boolean = false, mergePair: Boolean = false, onExpansionHaptic: () -> Unit) {
        automaticallyExpanded = false
        beganExpanded = expanded
        pressActive = true
        previewActive = hasMedia || expanded
        if (expanded) return
        mergingPair = mergePair || (mergingPair && transfer.value > 0f)
        secondary = fromSecondary || (secondary && transfer.value > 0f)
        mergingCompanions = mergeCompanions || mergingPair || (mergingCompanions && transfer.value > 0f)
        expansionAllowed = hasMedia
        val velocity = progress.velocity
        animation?.cancel()
        val needsAnticipation = progress.value == 0f
        scaleAnimation?.cancel()
        scaleAnimation = scope.launch {
            pressScale.animateTo(1.08f, tween(PRESS_PHASE_MS))
        }
        animation = scope.launch {
            if (hasMedia) coroutineScope {
                // Let the selected activity lead inward, then grow while the merge settles.
                // Waiting for a spring to reach exactly 1 creates a perceptible second beat.
                if (transferringActivities) launch {
                    transfer.animateTo(1f, tween(ActivityBubbleMotion.merge.durationMillis,
                        delayMillis = if (needsAnticipation) MERGE_INTENT_MS else 0,
                        easing = ActivityBubbleMotion.merge.easing))
                    returningFromCamera = false
                }
                if (needsAnticipation) scaleAnimation?.join()
                scaleAnimation = scope.launch { pressScale.animateTo(1f, anticipationSpring) }
                var vibrated = false
                val duration = IslandGrowth.durationForHold(longPressTimeoutMs).let { if (mergingPair) (it * .75f).toInt() else it }
                progress.animateTo(1f, IslandGrowth.spec(duration), initialVelocity = velocity) {
                    if (!vibrated && value >= IslandGrowth.hapticProgress && value < 1f) {
                        vibrated = true
                        onExpansionHaptic()
                    }
                }
            }
        }
    }

    /** Use the same placement and growth path without synthesizing a held touch. */
    fun expandAutomatically(fromSecondary: Boolean, mergeCompanions: Boolean) {
        automaticallyExpanded = true
        musicPeek = false
        animation?.cancel()
        scaleAnimation?.cancel()
        dragProgress = null
        pressActive = false
        expansionAllowed = true
        previewActive = true
        expanded = true
        secondary = fromSecondary
        mergingPair = false
        mergingCompanions = mergeCompanions
        animation = scope.launch {
            pressScale.snapTo(1f)
            coroutineScope {
                launch { transfer.animateTo(if (transferringActivities) 1f else 0f, ActivityBubbleMotion.merge) }
                progress.animateTo(1f, IslandGrowth.spec(IslandGrowth.durationForHold(500L)))
            }
        }
    }

    fun peekMusic(fromSecondary: Boolean, mergeCompanions: Boolean, mergePair: Boolean) {
        automaticallyExpanded = false
        animation?.cancel()
        scaleAnimation?.cancel()
        dragProgress = null
        pressActive = false
        expansionAllowed = true
        previewActive = true
        expanded = true
        musicPeek = true
        secondary = fromSecondary
        mergingPair = mergePair
        mergingCompanions = mergeCompanions || mergePair
        animation = scope.launch {
            pressScale.snapTo(1f)
            // Complete the merge before revealing the shallow metadata row.
            if (transferringActivities) transfer.animateTo(1f, ActivityBubbleMotion.merge)
            progress.animateTo(1f, IslandGrowth.spec(350))
        }
    }

    fun commit() {
        if (expansionAllowed) expanded = true else dismiss()
    }

    fun cancelPress() {
        pressActive = false
        if (!expanded) dismiss()
    }

    fun beginCompactDrag(): Boolean {
        if (!expanded) return false
        animation?.cancel()
        pressActive = false
        dragProgress = progress.value.coerceIn(0f, 1f)
        return true
    }

    fun dragCompact(delta: Float, distance: Float) {
        val current = dragProgress ?: return
        dragProgress = (current + delta / distance.coerceAtLeast(1f)).coerceIn(0f, 1f)
    }

    /** Project the release briefly so a quick upward flick also commits the collapse. */
    fun finishCompactDrag(velocity: Float, distance: Float, cancelled: Boolean = false): Boolean {
        val current = dragProgress ?: return false
        val progressVelocity = velocity / distance.coerceAtLeast(1f)
        val collapse = !cancelled && current + progressVelocity * .12f < .72f
        animation?.cancel()
        animation = scope.launch {
            progress.snapTo(current)
            dragProgress = null
            if (collapse) dismiss(progressVelocity)
            else progress.animateTo(1f, collapseSpring, initialVelocity = if (cancelled) 0f else progressVelocity)
        }
        return collapse
    }

    fun dismiss(initialVelocity: Float = progress.velocity) {
        automaticallyExpanded = false
        val start = fraction
        dragProgress = null
        pressActive = false
        expansionAllowed = false
        expanded = false
        scaleAnimation?.cancel()
        scaleAnimation = scope.launch { pressScale.animateTo(1f, reboundSpring) }
        val velocity = initialVelocity
        animation?.cancel()
        animation = scope.launch {
            progress.snapTo(start)
            collapseWithCompanions(velocity)
            returningFromCamera = false
            secondary = false
            mergingCompanions = false
            mergingPair = false
            previewActive = false
            musicPeek = false
        }
    }

    /** Keep the outgoing selection mounted until its card and companions finish exiting. */
    suspend fun dismissForReplacement(): Boolean {
        dismiss()
        val exit = animation ?: return false
        exit.join()
        return !exit.isCancelled
    }

    /** Restore companions behind any shrinking card before it reaches compact size. */
    private suspend fun collapseWithCompanions(velocity: Float) = coroutineScope {
        var returning = false
        fun restoreCompanions() {
            if (returning || !transferringActivities) return
            returning = true
            // A fully absorbed companion can change origin while still invisible.
            // Interrupted holds keep their existing path instead.
            if (secondary && transfer.value == 1f) returningFromCamera = true
            launch { transfer.animateTo(0f, ActivityBubbleMotion.departure) }
        }
        if (progress.value <= .65f) restoreCompanions()
        progress.animateTo(0f, collapseSpring, initialVelocity = velocity) {
            if (value <= .65f) restoreCompanions()
        }
        restoreCompanions()
    }

    fun tapRebound() {
        pressActive = false
        expansionAllowed = false
        scaleAnimation?.cancel()
        scaleAnimation = scope.launch {
            // A small extra lift on release, then a quick elastic return.
            pressScale.animateTo(maxOf(1.14f, pressScale.value), tween(65))
            pressScale.animateTo(1f, reboundSpring)
        }
        val velocity = progress.velocity
        animation?.cancel()
        animation = scope.launch {
            collapseWithCompanions(velocity)
            returningFromCamera = false
            secondary = false
            mergingCompanions = false
            mergingPair = false
            previewActive = false
            musicPeek = false
        }
    }
}

private const val MERGE_INTENT_MS = 80
private const val PRESS_PHASE_MS = 220
private val reboundSpring = spring<Float>(dampingRatio = 0.48f, stiffness = 900f, visibilityThreshold = 0.0005f)
private val anticipationSpring = spring<Float>(dampingRatio = 1f, stiffness = 1100f)
private val collapseSpring = spring<Float>(dampingRatio = 1f, stiffness = 195f)
