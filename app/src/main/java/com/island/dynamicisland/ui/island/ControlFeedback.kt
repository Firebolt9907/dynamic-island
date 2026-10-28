package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import com.island.dynamicisland.power.LocalInteractionMotionAllowed
import com.island.dynamicisland.power.LocalPowerSaveMode

/** Confirm actual source-state changes once; mounting a selected control is not an event. */
internal class ControlConfirmationMotion(initial: Boolean) {
    private var observed = initial
    val emphasis = Animatable(0f)

    suspend fun update(selected: Boolean, animate: Boolean) {
        val changed = selected != observed
        observed = selected
        if (!animate) emphasis.snapTo(0f)
        else if (changed) {
            emphasis.animateTo(1f, tween(90))
            emphasis.animateTo(0f, spring(dampingRatio = .6f, stiffness = 500f))
        }
    }
}

@Composable
internal fun rememberControlConfirmation(selected: Boolean, active: Boolean): State<Float> {
    val motion = remember { ControlConfirmationMotion(selected) }
    val enabled = active && !LocalPowerSaveMode.current && LocalInteractionMotionAllowed.current
    LaunchedEffect(selected, enabled) { motion.update(selected, enabled) }
    return motion.emphasis.asState()
}
