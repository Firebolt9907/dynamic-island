package com.island.dynamicisland.ui.island

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
import androidx.compose.ui.graphics.Color
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

@Composable
internal fun MusicPeek(label: String, headerHeight: Dp, topPadding: Dp, accent: Color, appear: () -> Float) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp).padding(top = topPadding, bottom = 6.dp)) {
        // Artwork and equalizer are the existing shared elements in the upper row.
        Spacer(Modifier.height(headerHeight))
        key(label) {
            Text(label, color = androidx.compose.ui.graphics.lerp(Color(0xFF9A9AA1), accent, .2f),
                fontSize = 12.sp, lineHeight = 16.sp,
                maxLines = 1, softWrap = false,
                modifier = Modifier.fillMaxWidth().expandedContentEntrance(appear, 0,
                    topPadding.value + headerHeight.value + 8f, headerHeight.value)
                    .basicMarquee(iterations = 1, initialDelayMillis = 900, velocity = 35.dp))
        }
    }
}
