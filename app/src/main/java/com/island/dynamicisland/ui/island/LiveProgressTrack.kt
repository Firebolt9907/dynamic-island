package com.island.dynamicisland.ui.island

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import com.island.dynamicisland.live.LiveProgressSegment
import com.island.dynamicisland.live.LiveProgressStyle

/** Preserve the source's stages and moving marker rather than a generic download bar. */
@Composable
internal fun LiveProgressTrack(style: LiveProgressStyle, progress: Float, accent: Color, active: Boolean = true) {
    val fraction = rememberLiveProgress(progress, active)
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        style.startIcon?.let { Image(it.asImageBitmap(), null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        BoxWithConstraints(Modifier.weight(1f).height(24.dp)) {
            val markerSize = 20.dp
            val markerInset = if (style.tracker != null) markerSize / 2 else 2.dp
            Canvas(Modifier.fillMaxSize()) {
                val start = markerInset.toPx()
                val width = (size.width - start * 2).coerceAtLeast(0f)
                val y = size.height / 2
                val gap = 3.dp.toPx()
                fun sourceColor(value: Int) = if (value ushr 24 == 0) accent else
                    Color(com.island.dynamicisland.live.readableTeamColor(value))
                fun line(from: Float, to: Float, color: Color, thickness: Float) {
                    // Keep round caps inside the segment so they cannot bridge a partition.
                    val radius = minOf(thickness / 2, (to - from) / 2)
                    if (radius > 0f) drawLine(color, Offset(from + radius, y), Offset(to - radius, y),
                        radius * 2, StrokeCap.Round)
                }
                var position = 0f
                val segments = style.segments.ifEmpty { listOf(LiveProgressSegment(1f, 0)) }
                segments.forEachIndexed { index, segment ->
                    val end = (position + segment.fraction).coerceIn(0f, 1f)
                    val left = start + position * width + if (index > 0) gap / 2 else 0f
                    val right = start + end * width - if (index < segments.lastIndex) gap / 2 else 0f
                    val color = sourceColor(segment.color)
                    line(left, right, color.copy(alpha = if (style.styledByProgress) .3f else 1f), 2.dp.toPx())
                    if (style.styledByProgress) line(left, (start + fraction.value * width).coerceAtMost(right), color, 4.dp.toPx())
                    position = end
                }
                style.points.forEach { point ->
                    drawCircle(sourceColor(point.color).copy(alpha =
                        if (style.styledByProgress && point.fraction > fraction.value) .4f else 1f),
                        3.dp.toPx(), Offset(start + point.fraction * width, y))
                }
            }
            style.tracker?.let { icon ->
                val travel = with(LocalDensity.current) { (maxWidth - markerSize).coerceAtLeast(0.dp).toPx() }
                Image(icon.asImageBitmap(), "Progress marker", Modifier
                    .offset { IntOffset((travel * fraction.value).roundToInt(), 0) }
                    .align(Alignment.CenterStart).size(markerSize))
            }
        }
        style.endIcon?.let { Spacer(Modifier.width(6.dp)); Image(it.asImageBitmap(), null, Modifier.size(18.dp)) }
    }
}
