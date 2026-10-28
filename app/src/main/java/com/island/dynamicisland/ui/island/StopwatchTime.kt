package com.island.dynamicisland.ui.island

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.island.dynamicisland.live.Stopwatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun stopwatchTime(clock: Stopwatch, precise: Boolean = false, active: Boolean = true): String {
    val time by produceState(clock.format(SystemClock.elapsedRealtime(), precise), clock, precise, active) {
        value = clock.format(SystemClock.elapsedRealtime(), precise)
        if (clock.startedAtElapsedMs != null && active) {
            while (isActive) {
                delay(if (precise) 50L else clock.nextCompactTickDelay(SystemClock.elapsedRealtime()))
                value = clock.format(SystemClock.elapsedRealtime(), precise)
            }
        }
    }
    return time
}
