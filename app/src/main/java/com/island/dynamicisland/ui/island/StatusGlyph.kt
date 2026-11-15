package com.island.dynamicisland.ui.island

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.live.AgentThread
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import com.island.dynamicisland.live.AgentActivity
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.island.dynamicisland.live.CompactStatusGlyph
import com.island.dynamicisland.power.LocalInteractionMotionAllowed
import com.island.dynamicisland.power.LocalPowerSaveMode
import kotlin.math.sin

/** The glyph slot is close to the leading icon's width, so the compact pill stays balanced. */
internal val CompactStatusGlyphWidth = 20.dp

private val AttentionColor = Color(0xFFFF9F0A)
private val DoneColor = Color(0xFF34C759)
private val FailedColor = Color(0xFFFF453A)

@Composable
internal fun CompactStatusGlyphView(glyph: CompactStatusGlyph, label: String, tint: Color,
                                    active: Boolean, modifier: Modifier = Modifier,
                                    size: Dp = CompactStatusGlyphWidth) {
    val animate = active && !LocalPowerSaveMode.current && LocalInteractionMotionAllowed.current
    Crossfade(glyph, modifier.size(size).semantics { contentDescription = label },
        animationSpec = tween(220), label = "statusGlyph") { state ->
        val phase = glyphPhase(animate, if (state == CompactStatusGlyph.WORKING) 1200 else 1400)
        Canvas(Modifier.size(size)) {
            // Glyphs are drawn around the centre at the compact size, then scaled to fit.
            val factor = size / CompactStatusGlyphWidth
            scale(factor, factor, center) {
                when (state) {
                    CompactStatusGlyph.WORKING -> typingDots(tint, phase.value, animate)
                    CompactStatusGlyph.ATTENTION -> attention(phase.value, animate)
                    CompactStatusGlyph.DONE -> badge(DoneColor) { check() }
                    CompactStatusGlyph.FAILED -> badge(FailedColor) { cross() }
                }
            }
        }
    }
}

/** A looping 0..1 phase, or a constant when motion is off so nothing recomposes per frame. */
@Composable
private fun glyphPhase(animate: Boolean, periodMs: Int): State<Float> {
    if (!animate) return remember { mutableFloatStateOf(0f) }
    return rememberInfiniteTransition(label = "statusGlyphPhase").animateFloat(0f, 1f,
        infiniteRepeatable(tween(periodMs, easing = LinearEasing), RepeatMode.Restart), label = "phase")
}

/** Three dots that rise and brighten in turn, like a typing indicator. */
private fun DrawScope.typingDots(tint: Color, phase: Float, animate: Boolean) {
    val radius = 2.dp.toPx()
    val gap = 3.dp.toPx()
    val span = radius * 6 + gap * 2
    val startX = (size.width - span) / 2 + radius
    val lift = 2.5.dp.toPx()
    for (i in 0..2) {
        // Each dot pulses during its own 40% window of the cycle, staggered by 15%.
        val local = ((phase - i * .15f) % 1f + 1f) % 1f
        val pulse = if (animate && local < .4f) sin(local / .4f * Math.PI).toFloat() else 0f
        val alpha = if (animate) .35f + .65f * pulse else .8f
        drawCircle(tint.copy(alpha = tint.alpha * alpha), radius,
            // Resting dots sit half a lift below centre, so the bounce reads as centred rather than high.
            Offset(startX + i * (radius * 2 + gap), size.height / 2 + (if (animate) lift / 2 else 0f) - lift * pulse))
    }
}

/** An amber "!" badge whose halo breathes so a waiting agent is noticeable at a glance. */
private fun DrawScope.attention(phase: Float, animate: Boolean) {
    val r = 8.dp.toPx()
    if (animate) {
        val breath = sin(phase * Math.PI).toFloat()
        drawCircle(AttentionColor.copy(alpha = .3f * breath), r + 2.dp.toPx() * breath)
    }
    drawCircle(AttentionColor, r)
    val c = center
    val w = 2.dp.toPx()
    drawLine(Color.Black, Offset(c.x, c.y - 4.dp.toPx()), Offset(c.x, c.y + .8.dp.toPx()), w, StrokeCap.Round)
    drawCircle(Color.Black, w * .6f, Offset(c.x, c.y + 3.6.dp.toPx()))
}

private fun DrawScope.badge(color: Color, mark: DrawScope.() -> Unit) {
    drawCircle(color, 8.dp.toPx())
    mark()
}

private fun DrawScope.check() {
    val u = 1.dp.toPx()
    val c = center
    val path = Path().apply {
        moveTo(c.x - 3.6f * u, c.y + .2f * u)
        lineTo(c.x - 1f * u, c.y + 2.7f * u)
        lineTo(c.x + 3.8f * u, c.y - 2.6f * u)
    }
    drawPath(path, Color.Black, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.cross() {
    val d = 3.dp.toPx()
    val w = 2.dp.toPx()
    val c = center
    drawLine(Color.Black, Offset(c.x - d, c.y - d), Offset(c.x + d, c.y + d), w, StrokeCap.Round)
    drawLine(Color.Black, Offset(c.x + d, c.y - d), Offset(c.x - d, c.y + d), w, StrokeCap.Round)
}

/** Space between a glyph and its count; the dots fill their slot edge to edge, so this carries the separation. */
private val AgentCountGap = 4.dp
private val ExpandedAgentCountGap = 6.dp
private val AgentSegmentGap = 8.dp
/** The expanded header has room for larger glyphs and counts than the compact pill. */
private val ExpandedGlyphSize = 26.dp
private const val ExpandedCountScale = 1.3f
/** Digits sit flush against their bounds, so a trailing count needs more room from the pill's edge than a glyph does. */
private val AgentCountTrailingPad = 5.dp
/** The dots are lighter than the leading logo, so they need a little more room from the pill's edge to look balanced. */
private val AgentGlyphTrailingPad = 3.dp

/** A lone thread in one state is already clear from the glyph, so "1" would only add noise. */
internal fun agentShowsCounts(segments: List<Pair<CompactStatusGlyph, Int>>): Boolean =
    !(segments.size == 1 && segments[0].second == 1)

private fun glyphColor(glyph: CompactStatusGlyph, accent: Color) = when (glyph) {
    CompactStatusGlyph.WORKING -> accent
    CompactStatusGlyph.ATTENTION -> AttentionColor
    CompactStatusGlyph.DONE -> DoneColor
    CompactStatusGlyph.FAILED -> FailedColor
}

private fun glyphNoun(glyph: CompactStatusGlyph) = when (glyph) {
    CompactStatusGlyph.WORKING -> "working"
    CompactStatusGlyph.ATTENTION -> "need input"
    CompactStatusGlyph.DONE -> "done"
    CompactStatusGlyph.FAILED -> "failed"
}

/** Ongoing states always show; finished states show briefly after a thread finishes, or when nothing else is left. */
internal fun agentSegments(agents: AgentActivity, finishedVisible: Boolean): List<Pair<CompactStatusGlyph, Int>> {
    val ongoing = listOf(CompactStatusGlyph.WORKING to agents.working, CompactStatusGlyph.ATTENTION to agents.attention)
        .filter { it.second > 0 }
    val finished = listOf(CompactStatusGlyph.DONE to agents.done, CompactStatusGlyph.FAILED to agents.failed)
        .filter { it.second > 0 }
    return if (finishedVisible || ongoing.isEmpty()) ongoing + finished else ongoing
}

/** True until the repository's finished-badge deadline passes, then flips once so the pill can shrink. */
@Composable
internal fun rememberFinishedBadgeVisible(until: Long): Boolean {
    var visible by remember(until) { mutableStateOf(until > System.currentTimeMillis()) }
    LaunchedEffect(until) {
        val remaining = until - System.currentTimeMillis()
        if (remaining > 0) { delay(remaining); visible = false } else visible = false
    }
    return visible
}

/**
 * How an agent summary splits across the compact pill, or null for the default "logo + every segment".
 * The logo is dropped for secondary bubbles and whenever multiple badge types are visible.
 * Mixed ongoing/finished badges split by state; otherwise the badges split evenly around the camera.
 */
internal data class AgentCompactParts(val leading: List<Pair<CompactStatusGlyph, Int>>,
                                      val trailing: List<Pair<CompactStatusGlyph, Int>>) {
    val showCounts: Boolean get() = agentShowsCounts(leading + trailing)
}

private fun CompactStatusGlyph.isFinished() = this == CompactStatusGlyph.DONE || this == CompactStatusGlyph.FAILED

@Composable
internal fun agentCompactParts(agents: AgentActivity?, grouped: Boolean = false, secondary: Boolean = false): AgentCompactParts? {
    agents ?: return null
    val segments = agentSegments(agents, rememberFinishedBadgeVisible(agents.finishedBadgeUntil))
    return splitAgentCompactParts(segments, grouped, secondary)
}

internal fun splitAgentCompactParts(segments: List<Pair<CompactStatusGlyph, Int>>,
                                    grouped: Boolean = false, secondary: Boolean = false): AgentCompactParts? {
    if (secondary) return AgentCompactParts(emptyList(), segments)
    if (segments.size < 2) return null
    // A grouped pill keeps all content on one side of the camera, so it only loses the logo.
    if (grouped) return AgentCompactParts(emptyList(), segments)
    val ongoing = segments.filterNot { it.first.isFinished() }
    val finished = segments.filter { it.first.isFinished() }
    if (ongoing.isNotEmpty() && finished.isNotEmpty()) return AgentCompactParts(ongoing, finished)
    val split = (segments.size + 1) / 2
    return AgentCompactParts(segments.take(split), segments.drop(split))
}

@Composable
internal fun agentSegmentsWidth(segments: List<Pair<CompactStatusGlyph, Int>>, style: TextStyle,
                                trailingPad: Boolean = true,
                                showCounts: Boolean = agentShowsCounts(segments)): Dp {
    if (segments.isEmpty()) return 0.dp
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val glyphs = CompactStatusGlyphWidth * segments.size + AgentSegmentGap * (segments.size - 1)
    if (!showCounts) return glyphs + if (trailingPad) AgentGlyphTrailingPad else 0.dp
    val counts = segments.sumOf { (_, count) ->
        with(density) { measurer.measure(count.toString(), style, maxLines = 1, softWrap = false).size.width.toDp() }.value.toDouble()
    }.dp
    return glyphs + AgentCountGap * segments.size + counts + if (trailingPad) AgentCountTrailingPad else 0.dp
}

@Composable
internal fun agentIndicatorWidth(agents: AgentActivity, style: TextStyle): Dp =
    agentSegmentsWidth(agentSegments(agents, rememberFinishedBadgeVisible(agents.finishedBadgeUntil)), style)

/** "••• 2  ! 1  ✓ 1": one glyph and count per state, so a multi-thread agent reads at a glance. */
@Composable
internal fun AgentSummaryIndicator(agents: AgentActivity, accent: Color, style: TextStyle,
                                   active: Boolean, modifier: Modifier, expanded: Boolean = false) {
    val finishedVisible = rememberFinishedBadgeVisible(agents.finishedBadgeUntil)
    // The expanded island has room for the full picture: every state, always with its count.
    AgentSegmentsRow(agentSegments(agents, finishedVisible || expanded), accent, style, active, modifier, expanded)
}

@Composable
internal fun AgentSegmentsRow(segments: List<Pair<CompactStatusGlyph, Int>>, accent: Color, style: TextStyle,
                              active: Boolean, modifier: Modifier, expanded: Boolean = false,
                              trailingPad: Boolean = true, arrangement: Arrangement.Horizontal = Arrangement.End,
                              showCounts: Boolean = expanded || agentShowsCounts(segments)) {
    val shown = segments.toMap()
    val countStyle = if (expanded) style.copy(fontSize = style.fontSize * ExpandedCountScale,
        lineHeight = style.lineHeight * ExpandedCountScale) else style
    Row(modifier.height(if (expanded) ExpandedGlyphSize else 20.dp), horizontalArrangement = arrangement, verticalAlignment = Alignment.CenterVertically) {
        var first = true
        for (glyph in CompactStatusGlyph.entries) {
            val count = shown[glyph] ?: continue
            if (!first) Spacer(Modifier.width(if (expanded) 12.dp else AgentSegmentGap))
            first = false
            key(glyph) {
                val tint = glyphColor(glyph, accent)
                Row(Modifier.semantics(mergeDescendants = true) { contentDescription = "$count ${glyphNoun(glyph)}" },
                    verticalAlignment = Alignment.CenterVertically) {
                    CompactStatusGlyphView(glyph, "", accent, active,
                        size = if (expanded) ExpandedGlyphSize else CompactStatusGlyphWidth)
                    if (showCounts) {
                        Spacer(Modifier.width(if (expanded) ExpandedAgentCountGap else AgentCountGap))
                        NumericTickerText(count.toString(), color = tint, style = countStyle, enabled = active, compact = true)
                    }
                }
            }
        }
        if (!expanded && trailingPad) Spacer(Modifier.width(if (showCounts) AgentCountTrailingPad else AgentGlyphTrailingPad))
    }
}

private val agentThreadOrder = listOf(CompactStatusGlyph.ATTENTION, CompactStatusGlyph.FAILED,
    CompactStatusGlyph.WORKING, CompactStatusGlyph.DONE)
private const val AgentThreadRowsMax = 4

/**
 * One row per thread: state glyph, title, and project, replacing the source's "Working <title> · <project>" text.
 * Threads needing the user come first; finished threads are dimmed so active work stands out.
 */
@Composable
internal fun AgentThreadList(threads: List<AgentThread>, accent: Color, active: Boolean, modifier: Modifier = Modifier) {
    val sorted = threads.sortedBy { agentThreadOrder.indexOf(it.state) }
    val shown = sorted.take(if (sorted.size > AgentThreadRowsMax) AgentThreadRowsMax - 1 else AgentThreadRowsMax)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        shown.forEach { thread ->
            val finished = thread.state == CompactStatusGlyph.DONE
            Row(Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "${thread.title}, ${glyphNoun(thread.state)}" +
                    (thread.project?.let { ", $it" } ?: "")
            }, verticalAlignment = Alignment.CenterVertically) {
                CompactStatusGlyphView(thread.state, "", accent, active, size = 18.dp)
                Spacer(Modifier.width(10.dp))
                Text(thread.title, color = Color.White.copy(alpha = if (finished) .55f else 1f),
                    fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                thread.project?.let { project ->
                    Spacer(Modifier.width(8.dp))
                    Text(project, color = Color(0xFF8E8E93).copy(alpha = if (finished) .7f else 1f),
                        fontSize = 13.sp, lineHeight = 18.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 120.dp))
                }
            }
        }
        if (sorted.size > shown.size) {
            Text("+${sorted.size - shown.size} more", color = Color(0xFF8E8E93), fontSize = 13.sp, lineHeight = 18.sp,
                modifier = Modifier.padding(start = 28.dp))
        }
    }
}
