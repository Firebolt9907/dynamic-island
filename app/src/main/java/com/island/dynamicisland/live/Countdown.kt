package com.island.dynamicisland.live

import java.util.Locale

/** Monotonic deadline, or a fixed remaining duration supplied by a paused timer. */
data class Countdown(val deadlineElapsedMs: Long, val pausedRemainingMs: Long? = null) {
    fun remainingAt(nowElapsedMs: Long): Long = (pausedRemainingMs ?: (deadlineElapsedMs - nowElapsedMs)).coerceAtLeast(0L)

    fun format(nowElapsedMs: Long): String {
        // Round up so a positive fraction of a second never reads as expired.
        val remaining = remainingAt(nowElapsedMs)
        val seconds = remaining / 1000 + if (remaining % 1000 > 0) 1 else 0
        return if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
        else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
    }

    internal fun nextTickDelay(nowElapsedMs: Long): Long =
        (remainingAt(nowElapsedMs) % 1000).takeIf { it > 0 } ?: 1000L
}
