package com.island.dynamicisland.media

/** Convert a media session snapshot to a bounded position without inventing an unknown timeline. */
internal object PlaybackTimeline {
    fun position(basePositionMs: Long, updatedAtMs: Long, nowMs: Long, durationMs: Long,
        playing: Boolean, speed: Float): Long {
        if (basePositionMs < 0L) return 0L
        val elapsed = if (playing && updatedAtMs > 0L && nowMs >= updatedAtMs && speed.isFinite()) {
            nowMs - updatedAtMs
        } else 0L
        val advance = if (elapsed > 0L) elapsed.toDouble() * speed else 0.0
        val upper = if (durationMs > 0L) durationMs.toDouble() else Long.MAX_VALUE.toDouble()
        return (basePositionMs.toDouble() + advance).coerceIn(0.0, upper).toLong()
    }
}
