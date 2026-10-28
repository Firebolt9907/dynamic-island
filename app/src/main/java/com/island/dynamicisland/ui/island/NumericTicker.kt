package com.island.dynamicisland.ui.island

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.LayoutDirection
import com.island.dynamicisland.power.LocalCompactTickerAllowed
import com.island.dynamicisland.power.LocalPowerSaveMode
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

internal const val NUMERIC_TICK_DURATION_MS = 360
internal const val COMPACT_TICK_FPS = 30
private const val NUMERIC_TICK_STAGGER_MS = 65
private const val NUMERIC_TICK_MAX_STAGGER_MS = 195

internal fun numericTickTotalDurationMs(digitCount: Int): Int = NUMERIC_TICK_DURATION_MS +
    ((digitCount - 1).coerceAtLeast(0) * NUMERIC_TICK_STAGGER_MS).coerceAtMost(NUMERIC_TICK_MAX_STAGGER_MS)

/** One shared clock, with a small left-to-right delay for each changing digit. */
internal fun numericTickDigitProgress(progress: Float, order: Int, digitCount: Int): Float {
    val duration = numericTickTotalDurationMs(digitCount)
    val delay = if (digitCount > 1)
        (duration - NUMERIC_TICK_DURATION_MS).toFloat() * order / (digitCount - 1) else 0f
    return ((progress * duration - delay) / NUMERIC_TICK_DURATION_MS).coerceIn(0f, 1f)
}

private const val COMPACT_FRAME_NANOS = 1_000_000_000L / COMPACT_TICK_FPS

/** Share a 30 Hz time grid so three readouts do not create three staggered redraw streams. */
internal fun compactTickTargetNanos(previousTargetNanos: Long, nowNanos: Long): Long =
    maxOf(previousTargetNanos + COMPACT_FRAME_NANOS,
        (nowNanos / COMPACT_FRAME_NANOS + 1L) * COMPACT_FRAME_NANOS)

internal fun compactTickDelayMs(targetNanos: Long, nowNanos: Long): Long =
    (targetNanos - nowNanos).coerceAtLeast(0L) / 1_000_000L

/** Rate-limit the animation clock itself, rather than sampling a full-rate animation. */
private class CompactTickFrameClock(private val parent: MonotonicFrameClock) : MonotonicFrameClock {
    private var targetNanos = 0L
    override suspend fun <R> withFrameNanos(onFrame: (Long) -> R): R {
        val now = System.nanoTime()
        targetNanos = compactTickTargetNanos(targetNanos, now)
        delay(compactTickDelayMs(targetNanos, now))
        return parent.withFrameNanos(onFrame)
    }
}
private val PushMotion = CubicBezierEasing(.34f, 0f, .18f, 1.28f)
private val EaseOutCubic = CubicBezierEasing(.215f, .61f, .355f, 1f)

internal data class TickingDigit(val index: Int, val before: Char, val after: Char)

/** Align by place value across carries, including 59:59 -> 1:00:00. */
internal fun tickingDigits(before: String, after: String): List<TickingDigit> = buildList {
    after.forEachIndexed { index, digit ->
        val old = before.getOrNull(index + before.length - after.length)
        if (digit in '0'..'9' && old != null && old in '0'..'9' && old != digit) {
            add(TickingDigit(index, old, digit))
        }
    }
}

private val NumericReadout = Regex("[0-9]+(?:[:,.][0-9]+)*")

/** Compare whole readouts, so a carried 9 -> 0 follows the other digits. */
internal fun numericTickDirection(before: String, after: String, index: Int): Float {
    val old = NumericReadout.findAll(before).toList()
    val next = NumericReadout.findAll(after).toList()
    val field = next.indexOfFirst { index in it.range }
    val previous = old.getOrNull(field + old.size - next.size) ?: return 1f
    val current = next.getOrNull(field) ?: return 1f
    fun magnitude(value: String) = value.replace(":", "").replace(",", "").toBigDecimalOrNull()
    val from = magnitude(previous.value) ?: return 1f
    val to = magnitude(current.value) ?: return 1f
    return if (to > from) 1f else -1f
}

/** Hundredths update directly; a 360 ms push must not restart twenty times per second. */
internal fun numericTickKey(value: String, fractionalTime: Boolean): String =
    if (fractionalTime) value.substringBefore('.') else value

private data class GlyphMask(val bitmap: Bitmap, val x: Int, val y: Int)
private data class DigitGlyph(val masks: List<GlyphMask>, val width: Int, val height: Int, val baseline: Float)

/** At most ten digits x nine ALPHA_8 masks per text style, created outside draw frames. */
private class DigitGlyphCache(
    private val measurer: TextMeasurer,
    private val style: TextStyle,
    private val density: androidx.compose.ui.unit.Density,
    private val direction: LayoutDirection,
) {
    private val glyphs = mutableMapOf<Char, DigitGlyph>()
    fun get(digit: Char): DigitGlyph = glyphs.getValue(digit)
    fun prepare(digit: Char) {
        if (digit in glyphs) return
        val layout = measurer.measure(digit.toString(), style.copy(color = Color.White), maxLines = 1, softWrap = false)
        val image = ImageBitmap(layout.size.width.coerceAtLeast(1), layout.size.height.coerceAtLeast(1))
        CanvasDrawScope().draw(density, direction, Canvas(image), Size(image.width.toFloat(), image.height.toFloat())) {
            drawText(layout)
        }
        val source = image.asAndroidBitmap()
        // WalkCloser uses 6 dp for 120 sp digits. Scale that blur down with our smaller type.
        val blur = minOf(6f * density.density, layout.size.height * .05f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        val masks = (0..8).map { step ->
            val sigma = blur * step / 8f
            paint.maskFilter = if (step == 0) null else BlurMaskFilter(
                ((sigma - .5f) / .57735f).coerceAtLeast(.01f), BlurMaskFilter.Blur.NORMAL)
            val offset = IntArray(2)
            GlyphMask(source.extractAlpha(paint, offset), offset[0], offset[1])
        }
        glyphs[digit] = DigitGlyph(masks, source.width, source.height, layout.firstBaseline)
    }
}

/**
 * Port of WalkCloser's CountdownTicker: the same 360 ms push, scale, alpha and focus curves.
 * One Canvas/Animatable per label. Only changed digits move; punctuation and units stay sharp.
 * Glyph blur masks are cached before the animation, never allocated in its draw loop.
 * No polling/frame clock when idle; system animation scale and battery saver are respected.
 */
@Composable
internal fun NumericTickerText(
    value: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    lineHeight: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign? = null,
    enabled: Boolean = true,
    fractionalTime: Boolean = false,
    compact: Boolean = false,
) {
    val merged = LocalTextStyle.current.merge(style).merge(TextStyle(
        color = color, fontSize = fontSize, lineHeight = lineHeight,
        fontWeight = fontWeight, fontFamily = fontFamily, textAlign = textAlign ?: TextAlign.Unspecified,
        fontFeatureSettings = "tnum"))
    val resolved = if (merged.color == Color.Unspecified) merged.copy(color = LocalContentColor.current) else merged
    val direction = LocalLayoutDirection.current
    val animate = enabled && !LocalPowerSaveMode.current && (!compact || LocalCompactTickerAllowed.current)
    // Keep normal shaping/bidi and paragraph handling for labels rather than numeric readouts.
    if (direction != LayoutDirection.Ltr || value.none { it in '0'..'9' } || '\n' in value) {
        Text(value, modifier, style = resolved, maxLines = 1, softWrap = false)
        return
    }
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer(cacheSize = 16)
    val cache = remember(measurer, resolved, density, direction) {
        DigitGlyphCache(measurer, resolved, density, direction)
    }
    val key = numericTickKey(value, fractionalTime)
    var previous by remember(cache) { mutableStateOf(key) }
    var tickDirections by remember(cache) { mutableStateOf(emptyMap<Int, Float>()) }
    var changes by remember(cache) { mutableStateOf(emptyList<TickingDigit>()) }
    val progress = remember(cache) { Animatable(1f) }
    LaunchedEffect(key, animate, cache, compact) {
        val old = previous
        previous = key
        changes = emptyList()
        if (!animate || old == key) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        val next = tickingDigits(old, key)
        next.forEach { cache.prepare(it.before); cache.prepare(it.after) }
        if (next.isNotEmpty()) {
            tickDirections = next.associate { it.index to numericTickDirection(old, key, it.index) }
            changes = next
            progress.snapTo(0f)
            val clock = currentCoroutineContext()[MonotonicFrameClock]
            if (compact && clock != null) {
                withContext(CompactTickFrameClock(clock)) {
                    progress.animateTo(1f, tween(numericTickTotalDurationMs(next.size), easing = LinearEasing))
                }
            } else progress.animateTo(1f, tween(numericTickTotalDurationMs(next.size), easing = LinearEasing))
            changes = emptyList()
        }
    }
    // Avoid a frame of the new digit before LaunchedEffect has prepared its transition.
    val pending = animate && key != previous
    val rendered = if (pending) previous + value.removePrefix(key) else value
    val layout = remember(measurer, rendered, resolved, density, direction) {
        measurer.measure(rendered, resolved, maxLines = 1, softWrap = false)
    }
    val moving = if (animate && !pending) changes else emptyList()
    val cells = remember(layout, moving) { moving.map { it to layout.getBoundingBox(it.index) } }
    val staticClip = remember(layout, cells) {
        Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, layout.size.width.toFloat(), layout.size.height.toFloat()))
            cells.forEach { (_, bounds) -> addRect(Rect(bounds.left, 0f, bounds.right, layout.size.height.toFloat())) }
        }
    }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG) }
    val alignment = when (resolved.textAlign) {
        TextAlign.Center -> Alignment.Center
        TextAlign.End, TextAlign.Right -> Alignment.CenterEnd
        else -> Alignment.CenterStart
    }
    Box(modifier.clearAndSetSemantics { text = AnnotatedString(value) }, contentAlignment = alignment) {
        Canvas(Modifier.size(with(density) { layout.size.width.toDp() }, with(density) { layout.size.height.toDp() })) {
            if (cells.isEmpty()) {
                drawText(layout)
            } else {
                clipPath(staticClip) { drawText(layout) }
                val sharedProgress = progress.value
                clipRect {
                    val canvas = drawContext.canvas.nativeCanvas
                    fun drawGlyph(digit: Char, bounds: Rect, alpha: Float, scale: Float, blur: Float, translation: Float) {
                        if (alpha <= 0f) return
                        val glyph = cache.get(digit)
                        val mask = glyph.masks[(blur * 8f).roundToInt().coerceIn(0, 8)]
                        canvas.save()
                        canvas.translate(bounds.center.x,
                            layout.firstBaseline - glyph.baseline + glyph.height / 2f + translation)
                        canvas.scale(scale, scale)
                        paint.color = resolved.color.toArgb()
                        paint.alpha = (alpha * resolved.color.alpha * 255f).toInt().coerceIn(0, 255)
                        canvas.drawBitmap(mask.bitmap, -glyph.width / 2f + mask.x, -glyph.height / 2f + mask.y, paint)
                        canvas.restore()
                    }
                    cells.forEachIndexed { order, (digit, bounds) ->
                        val raw = numericTickDigitProgress(sharedProgress, order, cells.size)
                        val pushed = PushMotion.transform(raw)
                        val distance = pushed.coerceIn(0f, 1f)
                        val entering = EaseOutCubic.transform(((raw - .02f) / .76f).coerceIn(0f, 1f))
                        val tickDirection = tickDirections[digit.index] ?: 1f
                        drawGlyph(digit.before, bounds, 1f - distance, 1f - .28f * distance,
                            distance, tickDirection * size.height * .32f * pushed)
                        drawGlyph(digit.after, bounds, entering, .72f + .28f * pushed,
                            1f - entering, -tickDirection * size.height * .82f * (1f - pushed))
                    }
                }
            }
        }
    }
}
