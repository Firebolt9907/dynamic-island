package com.island.dynamicisland.live

import java.util.Locale

/** Monotonic time base supplied by the notification; a paused watch has a fixed duration. */
data class Stopwatch(val startedAtElapsedMs: Long?, val pausedElapsedMs: Long = 0L) {
    fun elapsedAt(nowElapsedMs: Long): Long =
        (startedAtElapsedMs?.let { nowElapsedMs - it } ?: pausedElapsedMs).coerceAtLeast(0L)

    /** Wake on the next displayed second, rather than polling unchanged compact text. */
    internal fun nextCompactTickDelay(nowElapsedMs: Long): Long {
        val start = startedAtElapsedMs ?: return 1000L
        if (nowElapsedMs < start) return (start - nowElapsedMs).coerceAtMost(1000L)
        return 1000L - elapsedAt(nowElapsedMs) % 1000L
    }

    fun format(nowElapsedMs: Long, precise: Boolean = false): String {
        val elapsed = elapsedAt(nowElapsedMs)
        val seconds = elapsed / 1000
        val base = if (seconds >= 3600) String.format(Locale.ROOT, "%d:%02d:%02d", seconds / 3600, seconds / 60 % 60, seconds % 60)
            else String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
        return if (precise) base + String.format(Locale.ROOT, ".%02d", elapsed / 10 % 100) else base
    }
}
