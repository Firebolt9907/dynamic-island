package com.island.dynamicisland.ui.island

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

internal object ContentReplacementMotion {
    const val durationMs = 240
    const val edgeScale = .58f
    const val blurDp = 10f
    fun scale(visibility: Float): Float = edgeScale + (1f - edgeScale) * visibility.coerceIn(0f, 1f)
    fun blur(visibility: Float): Float = blurDp * (1f - visibility.coerceIn(0f, 1f))
}

/**
 * Port of boiler_fuel's AppCrossfadeText/AppBlurFadeTransition. Animate the
 * container's measured size alongside the replacement so the island follows it.
 * Children retain their natural geometry throughout; they are never reflowed
 * into intermediate dimensions or stretched to fit.
 * Use a semantic key (not a clock tick or bitmap instance) to identify changes.
 * Outgoing content is visual only and cannot retain actions/accessibility nodes.
 */
@Composable
internal fun <T> ContentUpdateCrossfade(
    state: T,
    modifier: Modifier = Modifier,
    stateKey: (T) -> Any? = { it },
    durationMs: Int = ContentReplacementMotion.durationMs,
    contentAlignment: Alignment = Alignment.Center,
    outgoingOnTop: Boolean = true,
    content: @Composable (T) -> Unit,
) {
    require(durationMs >= 0)
    val direction = LocalLayoutDirection.current
    val easing = CubicBezierEasing(.42f, 0f, .58f, 1f)
    AnimatedContent(targetState = state, modifier = modifier,
        contentAlignment = contentAlignment, contentKey = stateKey,
        transitionSpec = {
            (fadeIn(tween(durationMs, easing = easing)) togetherWith
                fadeOut(tween(durationMs, easing = easing))).apply {
                targetContentZIndex = if (outgoingOnTop) -1f else 1f
            }.using(SizeTransform(clip = false) { _, _ ->
                tween(durationMillis = durationMs, easing = easing)
            })
        }, label = "contentUpdate") { value ->
        val visibility by transition.animateFloat(
            transitionSpec = { tween(durationMs, easing = easing) }, label = "contentClarity",
        ) { if (it == EnterExitState.Visible) 1f else 0f }
        val current = stateKey(value) == stateKey(state)
        Box(Modifier
            .then(if (current) Modifier else Modifier.clearAndSetSemantics { }.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent(PointerEventPass.Initial).changes.forEach { it.consume() }
                }
            })
            .graphicsLayer {
                scaleX = ContentReplacementMotion.scale(visibility)
                scaleY = scaleX
                transformOrigin = TransformOrigin(contentAlignment.align(
                    androidx.compose.ui.unit.IntSize.Zero, androidx.compose.ui.unit.IntSize(100, 100),
                    direction).x / 100f,
                    contentAlignment.align(androidx.compose.ui.unit.IntSize.Zero,
                        androidx.compose.ui.unit.IntSize(100, 100), direction).y / 100f)
                val radius = ContentReplacementMotion.blur(visibility).dp.toPx()
                renderEffect = if (radius > .01f) BlurEffect(radius, radius, TileMode.Decal) else null
            }, contentAlignment = contentAlignment) { content(value) }
    }
}

private data class ContentUpdateTextValue(val text: String, val style: TextStyle, val replacementKey: Any?)

/** Small value replacement with the clipping and default timing of AppCrossfadeText. */
@Composable
internal fun ContentUpdateText(text: String, modifier: Modifier = Modifier,
                               style: TextStyle = LocalTextStyle.current,
                               maxLines: Int = Int.MAX_VALUE,
                               overflow: TextOverflow = TextOverflow.Clip,
                               contentAlignment: Alignment = Alignment.Center,
                               fillWidth: Boolean = false,
                               replacementKey: Any? = text) {
    ContentUpdateCrossfade(ContentUpdateTextValue(text, style, replacementKey), modifier.clipToBounds(),
        stateKey = { it.replacementKey }, contentAlignment = contentAlignment) { value ->
        Text(value.text, modifier = if (fillWidth) Modifier.fillMaxWidth() else Modifier,
            style = value.style, maxLines = maxLines, overflow = overflow)
    }
}
