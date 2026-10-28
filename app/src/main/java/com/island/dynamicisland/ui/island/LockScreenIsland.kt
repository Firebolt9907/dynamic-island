package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.config.IslandConfig
import kotlinx.coroutines.delay

/** A passive keyguard indicator: never intercept authentication or lock-screen gestures. */
@Composable
internal fun LockScreenIsland(locked: Boolean, keyguardShowing: Boolean) {
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    val camera by IslandConfig.cameraGuide.collectAsStateWithLifecycle()
    val entrance = remember { Animatable(0f) }
    val departure = remember { Animatable(1f) }
    val open by animateFloatAsState(if (locked) 0f else 1f,
        spring(dampingRatio = .7f, stiffness = 340f), label = "Lock shackle")
    LaunchedEffect(Unit) {
        entrance.animateTo(1f, spring(dampingRatio = .72f, stiffness = 300f))
    }
    LaunchedEffect(keyguardShowing) {
        departure.snapTo(1f)
        if (!keyguardShowing) {
            delay(650)
            departure.animateTo(0f, tween(350, easing = FastOutSlowInEasing))
        }
    }
    Canvas(Modifier.fillMaxSize().semantics {
        contentDescription = if (locked) "Device locked" else "Device unlocked"
    }) {
        val h = tuning.collapsedHeightDp.dp.toPx()
        val lensRadius = (camera?.horizontalRadiusDp ?: 8f).dp.toPx()
        val center = Offset(size.width / 2f +
            (camera?.centerXFromScreenCenterDp ?: tuning.centerXOffsetDp).dp.toPx(),
            (camera?.centerYDp ?: (tuning.topOffsetDp + tuning.collapsedHeightDp / 2f)).dp.toPx())
        val fullWidth = maxOf(tuning.collapsedWidthDp.dp.toPx(), lensRadius * 2f + 64.dp.toPx())
        val reveal = entrance.value * departure.value
        val w = lensRadius * 2f + (fullWidth - lensRadius * 2f) * reveal
        val height = lensRadius * 2f + (h - lensRadius * 2f) * reveal
        // Same 1 dp, 18% white outer edge as the neutral compact islands.
        val edge = 1.dp.toPx()
        drawRoundRect(Color.White.copy(alpha = .18f * reveal.coerceIn(0f, 1f)),
            Offset(center.x - w / 2f - edge, center.y - height / 2f - edge),
            Size(w + edge * 2f, height + edge * 2f), CornerRadius(height / 2f + edge))
        drawRoundRect(Color.Black, Offset(center.x - w / 2f, center.y - height / 2f),
            Size(w, height), CornerRadius(height / 2f))
        val iconCenter = Offset(center.x + lensRadius + 16.dp.toPx(), center.y)
        val color = androidx.compose.ui.graphics.lerp(Color.White, Color(0xFF92E6AC), open.coerceIn(0f, 1f))
            .copy(alpha = reveal.coerceIn(0f, 1f))
        withTransform({
            scale(.8f + .2f * entrance.value, .8f + .2f * entrance.value, iconCenter)
            rotate(-8f * open * (1f - open), iconCenter)
        }) {
            val u = 1.dp.toPx()
            val x = iconCenter.x
            val y = iconCenter.y
            drawRoundRect(color, Offset(x - 6 * u, y), Size(12 * u, 9 * u), CornerRadius(2 * u))
            val lift = open * 4 * u
            val shackle = Path().apply {
                moveTo(x - 4 * u, y + u)
                lineTo(x - 4 * u, y - 4 * u - lift)
                cubicTo(x - 4 * u, y - 10 * u - lift, x + 4 * u, y - 10 * u - lift,
                    x + 4 * u, y - 4 * u - lift)
                lineTo(x + 4 * u, y - open * 4 * u - lift)
            }
            drawPath(shackle, color, style = Stroke(2 * u, cap = StrokeCap.Round))
            drawCircle(Color.Black.copy(alpha = reveal.coerceIn(0f, 1f)), u, Offset(x, y + 4 * u))
        }
    }
}
