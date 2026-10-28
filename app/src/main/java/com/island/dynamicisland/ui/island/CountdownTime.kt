package com.island.dynamicisland.ui.island

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.island.dynamicisland.live.Countdown
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
internal fun countdownTime(clock: Countdown, active: Boolean = true): String {
    val time by produceState(clock.format(SystemClock.elapsedRealtime()), clock, active) {
        value = clock.format(SystemClock.elapsedRealtime())
        while (active && clock.pausedRemainingMs == null && isActive && clock.remainingAt(SystemClock.elapsedRealtime()) > 0L) {
            delay(clock.nextTickDelay(SystemClock.elapsedRealtime()))
            value = clock.format(SystemClock.elapsedRealtime())
        }
    }
    return time
}
