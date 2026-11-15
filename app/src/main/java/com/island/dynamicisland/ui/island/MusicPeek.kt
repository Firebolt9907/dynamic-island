package com.island.dynamicisland.ui.island

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.island.dynamicisland.media.NowPlaying
import com.island.dynamicisland.media.TrackIdentity

internal class MusicTrackChangeTracker {
    private var previous: TrackIdentity? = null
    fun changed(track: NowPlaying?): Boolean {
        val identity = track?.trackIdentity
        val changed = previous != null && identity != null && previous != identity
        previous = identity
        return changed
    }
}

internal fun musicPeekLabel(track: NowPlaying?): String =
    listOfNotNull(track?.title?.takeIf { it.isNotBlank() }, track?.artist?.takeIf { it.isNotBlank() }).joinToString(" · ")

/** Allow one complete scroll at 35 dp/sec, plus time to merge, read and settle. */
internal fun musicPeekDuration(textWidthDp: Float, availableWidthDp: Float): Long =
    (2_600L + ((textWidthDp - availableWidthDp).coerceAtLeast(0f) / 35f * 1_000).toLong())
        .coerceIn(4_500L, 30_000L)

internal val MusicPeekHorizontalPadding = 16.dp

/** Shared by layout and the scroll-duration estimate so a long title gets one full pass. */
internal val MusicPeekTextStyle = TextStyle(fontSize = 13.sp, lineHeight = 17.sp,
    fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp)

/** Title in white, artist dimmed and tinted toward the artwork, on one scrolling line. */
internal fun musicPeekText(track: NowPlaying?, accent: Color): AnnotatedString = buildAnnotatedString {
    val title = track?.title?.takeIf { it.isNotBlank() }
    val artist = track?.artist?.takeIf { it.isNotBlank() }
    if (title != null) withStyle(SpanStyle(color = Color.White)) { append(title) }
    if (title != null && artist != null) withStyle(SpanStyle(color = Color(0xFF6E6E74))) { append("  ·  ") }
    if (artist != null) withStyle(SpanStyle(color = lerp(Color(0xFFA1A1A8), accent, .28f),
        fontWeight = FontWeight.Medium)) { append(artist) }
}

@Composable
internal fun MusicPeek(track: NowPlaying?, headerHeight: Dp, topPadding: Dp, accent: Color, appear: () -> Float) {
    val text = musicPeekText(track, accent)
    Column(Modifier.fillMaxWidth().padding(horizontal = MusicPeekHorizontalPadding)
        .padding(top = topPadding, bottom = 9.dp)) {
        // Artwork and equalizer are the existing shared elements in the upper row.
        Spacer(Modifier.height(headerHeight + 2.dp))
        key(text.text) {
            Text(text, style = MusicPeekTextStyle,
                maxLines = 1, softWrap = false,
                modifier = Modifier.fillMaxWidth().expandedContentEntrance(appear, 0,
                    topPadding.value + headerHeight.value + 10f, headerHeight.value)
                    .marqueeEdgeFade()
                    .basicMarquee(iterations = 1, initialDelayMillis = 1_100, velocity = 32.dp,
                        spacing = MarqueeSpacing(28.dp)))
        }
    }
}

/** Soften both ends so scrolling text slides under the edges instead of being cut off. */
private fun Modifier.marqueeEdgeFade(): Modifier = graphicsLayer {
    compositingStrategy = CompositingStrategy.Offscreen
}.drawWithContent {
    drawContent()
    val fade = 10.dp.toPx().coerceAtMost(size.width / 4f)
    if (fade <= 0f) return@drawWithContent
    drawRect(Brush.horizontalGradient(
        0f to Color.Transparent, fade / size.width to Color.Black,
        1f - fade / size.width to Color.Black, 1f to Color.Transparent),
        blendMode = BlendMode.DstIn)
}
