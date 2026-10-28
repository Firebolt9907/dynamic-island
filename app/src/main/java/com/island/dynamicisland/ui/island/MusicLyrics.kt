package com.island.dynamicisland.ui.island

import androidx.compose.animation.togetherWith
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.lyrics.*
import com.island.dynamicisland.media.NowPlaying
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToLong

@Composable
internal fun MusicLyrics(np: NowPlaying?, active: Boolean, previewPositionMs: Long?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val track = LyricsTrack.from(np)
    var lyrics by remember(track) { mutableStateOf<SyncedLyrics?>(null) }
    LaunchedEffect(track, active) {
        if (!active || track == null) return@LaunchedEffect
        repeat(3) { attempt ->
            lyrics = try { LyricsRepository.lookup(context.applicationContext, track) }
                catch (e: CancellationException) { throw e } catch (_: Exception) { null }
            if (lyrics != null) return@LaunchedEffect
            if (attempt < 2) delay(if (attempt == 0) 2000L else 5000L)
        }
    }
    val loaded = lyrics ?: return
    val key = track?.key ?: return
    val prefs = remember(context) { context.getSharedPreferences("lyrics_timing", Context.MODE_PRIVATE) }
    val appKey = "app:${np?.packageName}"
    val offsetMs = remember(key, appKey) { prefs.getInt("$appKey:$key", prefs.getInt(appKey, 0)).coerceIn(-10000, 10000) }
    val motionAllowed = active && !com.island.dynamicisland.power.LocalPowerSaveMode.current &&
        com.island.dynamicisland.power.LocalInteractionMotionAllowed.current
    var overflowingLine by remember(loaded) { mutableStateOf<LyricLine?>(null) }
    val position by produceState((np?.positionAt(SystemClock.elapsedRealtime()) ?: 0L) + offsetMs,
        np, loaded, active, motionAllowed, offsetMs, previewPositionMs, overflowingLine) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            val position = (previewPositionMs ?: np?.positionAt(now) ?: 0L) + offsetMs
            value = position
            if (!active || previewPositionMs != null || np?.advancesAt(now) != true) break
            val singingWord = loaded.lines.getOrNull(loaded.lineAt(position))?.segments?.any {
                position >= it.startMs && position < it.endMs
            } == true
            val shortGap = shortLyricGapAt(loaded, position, np.durationMs)
            val scrollingLine = overflowingLine?.let { position >= it.startMs && position < it.endMs } == true
            if (motionAllowed && (singingWord || shortGap != null || scrollingLine)) withFrameNanos { }
            else delay(lyricTickDelay(loaded.boundaries, position, np.playbackSpeed))
        }
    }
    val index = loaded.lineAt(position)
    // Keep the playback clock alive during gaps, but contribute no lyric height.
    val current = loaded.lines.getOrNull(index)?.takeIf { hasVisibleLyrics(it.text) }
    val shortGap = if (current == null) shortLyricGapAt(loaded, position, np?.durationMs ?: 0L) else null
    if (current == null && shortGap == null) return
    val style = androidx.compose.material3.LocalTextStyle.current.merge(androidx.compose.ui.text.TextStyle(
        fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold,
    ))
    androidx.compose.animation.AnimatedContent(
        targetState = current,
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp).height(24.dp),
        contentAlignment = Alignment.CenterStart,
        transitionSpec = {
            if (motionAllowed) {
                ((androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(240)) +
                    androidx.compose.animation.slideInVertically(androidx.compose.animation.core.tween(240)) { it / 3 }) togetherWith
                    (androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(160)) +
                        androidx.compose.animation.slideOutVertically(androidx.compose.animation.core.tween(200)) { -it / 3 }))
            } else {
                androidx.compose.animation.EnterTransition.None togetherWith androidx.compose.animation.ExitTransition.None
            }
        }, label = "lyricLine",
    ) { line ->
        if (line == null) {
            val progress = shortGap?.let { lyricWordProgress(position, it.startMs, it.endMs) } ?: 1f
            androidx.compose.foundation.Canvas(Modifier.fillMaxWidth().height(24.dp)) {
                repeat(3) { dot ->
                    val growth = androidx.compose.animation.core.FastOutSlowInEasing.transform(
                        (progress * 3f - dot).coerceIn(0f, 1f))
                    drawCircle(Color.White.copy(alpha = .35f + .65f * growth),
                        radius = (1.6f + 1.2f * growth).dp.toPx(),
                        center = androidx.compose.ui.geometry.Offset((4f + dot * 11f).dp.toPx(), size.height / 2))
                }
            }
        } else {
            val plainText = line.text.replace('\n', ' ').replace('\r', ' ')
            val text = buildAnnotatedString {
                // Preserve timing character ranges while keeping even multiline TTML paragraphs on one row.
                append(plainText)
                line.segments.forEach { segment ->
                    val future = Color(0xFF6E6E76)
                    val brightness = if (motionAllowed) {
                        androidx.compose.animation.core.FastOutSlowInEasing.transform(
                            lyricWordProgress(position, segment.startMs, minOf(segment.endMs, segment.startMs + 160L)))
                    } else if (position >= segment.startMs) 1f else 0f
                    addStyle(SpanStyle(
                        color = androidx.compose.ui.graphics.lerp(future, Color.White, brightness),
                    ), segment.from, segment.until)
                }
            }

            val lifts = if (motionAllowed) line.segments.mapNotNull { segment ->
                val amount = lyricSyllableLift(lyricWordProgress(position, segment.startMs, segment.endMs)) * 14f
                if (amount > .01f) TextGlyphLift(segment.from, segment.until, amount) else null
            } else emptyList()
            val scrollProgress = if (line.segments.isEmpty()) {
                if (motionAllowed) lyricLineScrollProgress(position, line.startMs, line.endMs) else 0f
            } else null
            FadingMusicText(text, Color.White, style, Modifier,
                interactive = motionAllowed && line == current, glyphLifts = lifts,
                scrollProgress = scrollProgress,
                onOverflowChanged = { overflowing ->
                    if (line == current) overflowingLine = line.takeIf { overflowing && it.segments.isEmpty() }
                })
        }
    }
}

internal data class LyricGap(val startMs: Long, val endMs: Long)

/** Retain the row only for brief, bounded gaps; the dots follow playback, not a separate timer. */
internal fun shortLyricGapAt(lyrics: SyncedLyrics, position: Long, duration: Long): LyricGap? {
    val line = lyrics.lines.getOrNull(lyrics.lineAt(position))
    if (line != null && hasVisibleLyrics(line.text)) return null
    // Treat adjacent note placeholders and empty spans as one instrumental gap.
    var start = 0L
    var end = duration
    for (candidate in lyrics.lines) {
        if (!hasVisibleLyrics(candidate.text)) continue
        if (candidate.endMs <= position) start = maxOf(start, candidate.endMs)
        if (candidate.startMs > position) end = minOf(end, candidate.startMs)
    }
    return if (end - start in 1..2000 && position >= start && position < end) LyricGap(start, end) else null
}

/** Provider music-note placeholders and empty instrumental gaps do not reserve a row. */
internal fun hasVisibleLyrics(text: String): Boolean = text.codePoints().anyMatch { code ->
    when (code) {
        0x2669, 0x266A, 0x266B, 0x266C, 0x1F3B5, 0x1F3B6, 0xFE0F -> false
        else -> !Character.isWhitespace(code)
    }
}

/** Traverse once over the visible line's lifetime, with short reading holds at both ends. */
internal fun lyricLineScrollProgress(position: Long, start: Long, end: Long): Float {
    val duration = (end - start).coerceAtLeast(1L)
    val lead = minOf(600L, duration * 12 / 100)
    val tail = minOf(500L, duration * 12 / 100)
    return lyricWordProgress(position, start + lead, end - tail)
}

/** A small upward emphasis returns gently to baseline by the syllable's end. */
internal fun lyricSyllableLift(progress: Float): Float =
    (kotlin.math.sin(kotlin.math.PI * progress.coerceIn(0f, 1f)) * .10).toFloat()

/** Playback-clock timing freezes naturally on pause and follows seeks immediately. */
internal fun lyricWordProgress(position: Long, start: Long, end: Long): Float =
    if (end <= start) { if (position >= end) 1f else 0f }
    else ((position - start).toDouble() / (end - start)).toFloat().coerceIn(0f, 1f)

/** Wake at real timing boundaries between words; paused and hidden cards schedule no ticks. */
internal fun lyricTickDelay(boundaries: List<Long>, position: Long, speed: Float): Long {
    if (!speed.isFinite() || speed == 0f) return 1000L
    if (speed < 0 && boundaries.binarySearch(position) >= 0) return 16L
    val next = if (speed > 0) boundaries.firstOrNull { it > position } else boundaries.lastOrNull { it < position }
    return next?.let { (abs(it - position) / abs(speed.toDouble())).roundToLong().coerceIn(16L, 1000L) } ?: 1000L
}
