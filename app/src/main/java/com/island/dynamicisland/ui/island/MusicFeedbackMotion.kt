package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember

internal fun artworkPlaybackScale(playing: Boolean): Float = if (playing) 1f else .82f
internal fun timelineScrubEmphasis(scrubbing: Boolean): Float = if (scrubbing) 1f else 0f

/** Reduced motion changes the transition, never the feedback's visible target. */
internal class MusicFeedbackMotion(initial: Float, private val stiffness: Float) {
    val progress = Animatable(initial)
    suspend fun update(target: Float, animate: Boolean) {
        if (animate) progress.animateTo(target, spring(dampingRatio = .8f, stiffness = stiffness))
        else progress.snapTo(target)
    }
}

@Composable
internal fun rememberArtworkPlaybackScale(playing: Boolean, animate: Boolean): State<Float> {
    val motion = remember { MusicFeedbackMotion(artworkPlaybackScale(playing), stiffness = 220f) }
    LaunchedEffect(playing, animate) { motion.update(artworkPlaybackScale(playing), animate) }
    return motion.progress.asState()
}

@Composable
internal fun rememberTimelineScrubEmphasis(scrubbing: Boolean, animate: Boolean): State<Float> {
    val motion = remember { MusicFeedbackMotion(timelineScrubEmphasis(scrubbing), stiffness = 500f) }
    LaunchedEffect(scrubbing, animate) { motion.update(timelineScrubEmphasis(scrubbing), animate) }
    return motion.progress.asState()
}
