package com.island.dynamicisland.ui.island

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import kotlin.math.roundToInt
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.island.dynamicisland.audio.BluetoothAudioEvent
import com.island.dynamicisland.config.IslandConfig

/** Keep the existing island composed underneath so music and activity motion retain their state. */
@Composable
internal fun BluetoothAudioPresentation(
    event: BluetoothAudioEvent?,
    onBoundsChanged: (Int, Int, Int, Int, Boolean) -> Unit,
    content: @Composable (Boolean, (Int, Int, Int, Int, Boolean) -> Unit) -> Unit,
) {
    val tuning by IslandConfig.tuning.collectAsStateWithLifecycle()
    val density = LocalDensity.current
    val textMeasurer = rememberTextMeasurer()
    val progress = remember { Animatable(0f) }
    val motionAllowed = com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
    var retained by remember { mutableStateOf(event) }
    var bounds by remember { mutableStateOf<ActivityTouchBounds?>(null) }
    var origin by remember { mutableStateOf<ActivityTouchBounds?>(null) }
    LaunchedEffect(event) {
        if (event != null) {
            if (retained == null) origin = bounds
            retained = event
            if (motionAllowed) progress.animateTo(1f, IslandGrowth.spec(700))
            else progress.snapTo(1f)
        } else {
            if (motionAllowed) progress.animateTo(0f, IslandGrowth.spec(600))
            else progress.snapTo(0f)
            retained = null
        }
    }
    val presenting = retained != null
    Box(Modifier.fillMaxSize()) {
        Box(Modifier.graphicsLayer {
            val departure = ExpandedContentMotion.fraction(progress.value, 0)
            alpha = 1f - departure
            val radius = (8f * departure).dp.toPx()
            renderEffect = if (motionAllowed && radius > .01f && alpha > 0f)
                BlurEffect(radius, radius, TileMode.Decal) else null
        }) {
            content(presenting) { left, top, width, height, capture ->
                val next = ActivityTouchBounds("bluetooth-origin", left, top, width, height)
                if (width > 0 && height > 0 && bounds != next) bounds = next
                if (!presenting) onBoundsChanged(left, top, width, height, capture)
            }
        }
        val shown = retained
        if (shown != null) {
            SideEffect { onBoundsChanged(0, 0, 0, 0, false) }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val p = progress.value.coerceIn(0f, 1f)
                val widthProgress = IslandGrowth.widthFraction(p)
                val heightProgress = IslandGrowth.heightFraction(p)
                val startWidth = origin?.let { with(density) { it.width.toDp() } } ?: tuning.collapsedWidthDp.dp
                val startHeight = origin?.let { with(density) { it.height.toDp() } } ?: tuning.collapsedHeightDp.dp
                val startLeft = origin?.let { with(density) { it.left.toDp() } }
                    ?: ((maxWidth - startWidth) / 2 + tuning.centerXOffsetDp.dp)
                val startTop = origin?.let { with(density) { it.top.toDp() } } ?: tuning.topOffsetDp.dp
                val endWidth = minOf(390.dp, maxWidth - 20.dp)
                val endLeft = (maxWidth - endWidth) / 2 + tuning.centerXOffsetDp.dp
                val inset = 12.dp
                val textWidth = (endWidth - inset * 2 - 44.dp - 42.dp - 24.dp).coerceAtLeast(48.dp)
                val nameStyle = TextStyle(color = Color(0xFFF5F5F7), fontSize = 17.sp,
                    lineHeight = 21.sp, fontWeight = FontWeight.Medium)
                val statusStyle = TextStyle(color = Color(0xFF858589), fontSize = 13.sp, lineHeight = 17.sp)
                val textConstraints = Constraints(maxWidth = with(density) { textWidth.roundToPx() })
                val nameSize = textMeasurer.measure(shown.name, nameStyle, constraints = textConstraints).size
                val statusSize = textMeasurer.measure(if (shown.connected) "Connected" else "Disconnected",
                    statusStyle, constraints = textConstraints).size
                val textHeight = with(density) { (nameSize.height + statusSize.height).toDp() } + 2.dp
                val rowHeight = maxOf(48.dp, textHeight)
                val endHeight = rowHeight + inset * 2
                val width = androidx.compose.ui.unit.lerp(startWidth, endWidth, widthProgress)
                val height = androidx.compose.ui.unit.lerp(startHeight, endHeight, heightProgress)
                Box(Modifier.offset(androidx.compose.ui.unit.lerp(startLeft, endLeft, widthProgress), startTop)
                    .size(width, height).graphicsLayer { alpha = (progress.value / .18f).coerceIn(0f, 1f) }.clip(RoundedCornerShape(androidx.compose.ui.unit.lerp(startHeight / 2, endHeight / 2, p)))
                    .background(Color.Black)) {
                    Row(Modifier.align(Alignment.Center).requiredWidth(endWidth)
                        .padding(horizontal = inset).requiredHeight(rowHeight)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        BluetoothDeviceIllustration(shown.name, Modifier.size(44.dp)
                            .bluetoothContentEntrance({ progress.value }, 0, endHeight.value / 2,
                                endHeight.value, motionAllowed, endWidth.value / 2 - inset.value - 22f))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                            Text(if (shown.connected) "Connected" else "Disconnected",
                                style = statusStyle, modifier = Modifier.bluetoothContentEntrance(
                                    { progress.value }, 1, inset.value + with(density) { statusSize.height.toDp().value } / 2,
                                    endHeight.value, motionAllowed))
                            Text(shown.name, style = nameStyle, modifier = Modifier.bluetoothContentEntrance(
                                { progress.value }, 2, endHeight.value / 2 + with(density) { statusSize.height.toDp().value } / 2,
                                endHeight.value, motionAllowed))
                        }
                        Box(Modifier.bluetoothContentEntrance({ progress.value }, 0, endHeight.value / 2,
                            endHeight.value, motionAllowed, -(endWidth.value / 2 - inset.value - 21f))) {
                            key(shown.sequence) { BluetoothBatteryRing(shown.batteryLevel) }
                        }
                    }
                }
            }
        }
    }
}

/** Neutral accessory art: no invented battery percentage or brand-specific asset. */
@Composable
private fun BluetoothDeviceIllustration(name: String, modifier: Modifier) {
    val earbuds = name.contains("pod", true) || name.contains("bud", true)
    Canvas(modifier) {
        val unit = size.minDimension / 64f
        fun pt(x: Float, y: Float) = Offset(x * unit, y * unit)
        val ceramic = Brush.linearGradient(listOf(Color(0xFFFFFFFF), Color(0xFFDADDE0), Color(0xFF8D9398)), pt(8f, 8f), pt(53f, 58f))
        if (earbuds) {
            for (right in listOf(false, true)) {
                val x = if (right) 40f else 14f
                drawRoundRect(ceramic, pt(x, 26f), Size(9f * unit, 32f * unit), CornerRadius(4f * unit))
                drawOval(ceramic, pt(x - 7f, 7f), Size(23f * unit, 27f * unit))
                drawOval(Color(0xFF33373A), pt(x + (if (right) 5f else -3f), 12f), Size(6f * unit, 10f * unit))
                drawOval(Color(0xFFEBEDEF), pt(x + (if (right) -10f else 10f), 20f), Size(13f * unit, 15f * unit))
                drawOval(Color(0xFF747A7F), pt(x + (if (right) -8f else 14f), 24f), Size(6f * unit, 8f * unit))
                drawLine(Color(0xFF7E858A), pt(x + 2f, 55f), pt(x + 7f, 55f), 2f * unit, StrokeCap.Round)
            }
        } else {
            drawArc(ceramic, 180f, 180f, false, pt(8f, 5f), Size(48f * unit, 51f * unit), style = Stroke(7f * unit, cap = StrokeCap.Round))
            drawRoundRect(ceramic, pt(5f, 29f), Size(15f * unit, 28f * unit), CornerRadius(7f * unit))
            drawRoundRect(ceramic, pt(44f, 29f), Size(15f * unit, 28f * unit), CornerRadius(7f * unit))
            drawRoundRect(Color(0xFF747A80), pt(15f, 33f), Size(6f * unit, 20f * unit), CornerRadius(3f * unit))
            drawRoundRect(Color(0xFF747A80), pt(43f, 33f), Size(6f * unit, 20f * unit), CornerRadius(3f * unit))
        }
    }
}

@Composable
private fun BluetoothBatteryRing(level: Int?) {
    val fill = remember { Animatable(0f) }
    val motionAllowed = com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
    LaunchedEffect(level, motionAllowed) {
        val target = level?.toFloat() ?: 0f
        if (motionAllowed) fill.animateTo(target, tween(950, easing = FastOutSlowInEasing))
        else fill.snapTo(target)
    }
    val color = if (level != null && level <= 20) Color(0xFFFF453A) else Color(0xFF30D158)
    Box(Modifier.size(42.dp).semantics {
        contentDescription = level?.let { "Device battery $it percent" } ?: "Device battery unavailable"
    }, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize().padding(2.dp)) {
            val stroke = 3.dp.toPx()
            drawCircle(color.copy(alpha = .22f), style = Stroke(stroke))
            if (level != null) drawArc(color, -90f, 360f * fill.value / 100f, false,
                style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Text(if (level != null) fill.value.roundToInt().toString() else "–", color = if (level != null) color else Color(0xFF858589),
            fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Each element starts at the camera center and follows the silhouette's reversible growth. */
private fun Modifier.bluetoothContentEntrance(
    progress: () -> Float, order: Int, centerY: Float, fullHeight: Float,
    motionAllowed: Boolean, horizontalTravel: Float = 0f,
): Modifier = if (!motionAllowed) this else graphicsLayer {
    val p = progress().coerceIn(0f, 1f)
    val reveal = ExpandedContentMotion.fraction(p, order)
    alpha = reveal
    scaleX = .55f + .45f * reveal
    scaleY = .55f + .45f * reveal
    translationX = horizontalTravel.dp.toPx() * (1f - IslandGrowth.widthFraction(p))
    translationY = (fullHeight / 2f - centerY).dp.toPx() * (1f - IslandGrowth.heightFraction(p))
    val radius = ExpandedContentMotion.blurRadiusDp(p).dp.toPx()
    renderEffect = if (alpha > 0f && radius > .01f) BlurEffect(radius, radius, TileMode.Decal) else null
    clip = false
}
