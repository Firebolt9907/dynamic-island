package com.island.dynamicisland.ui.island

import android.os.SystemClock
import kotlinx.coroutines.delay
import androidx.compose.runtime.produceState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import com.island.dynamicisland.media.NowPlaying

/** Only a visible expanded timeline needs clock updates; compact music has no progress bar. */
@Composable
internal fun rememberPlaybackProgress(np: NowPlaying?, active: Boolean = true): State<Float> =
    produceState(progressAt(np, SystemClock.elapsedRealtime()), np, active) {
        while (true) {
            val now = SystemClock.elapsedRealtime()
            value = progressAt(np, now)
            if (!active || np?.advancesAt(now) != true) break
            // Four small UI updates a second, without media IPC or whole-repository emissions.
            delay(250L)
        }
    }

private fun progressAt(np: NowPlaying?, now: Long): Float =
    if (np != null && np.durationMs > 0L) {
        (np.positionAt(now).toDouble() / np.durationMs).toFloat().coerceIn(0f, 1f)
    } else 0f
