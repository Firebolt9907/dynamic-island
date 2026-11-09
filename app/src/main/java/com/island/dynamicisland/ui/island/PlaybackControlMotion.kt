package com.island.dynamicisland.ui.island

internal data class ToggleFrame(val scale: Float, val blend: Float)

internal data class SkipTriangle(val left: Float, val scale: Float, val alpha: Float)

/** A replacement grows at the back while the two original triangles advance one slot. */
internal object PlaybackControlMotion {
    /** One timeline: zero velocity at each turn, with no tween/spring handoff. */
    fun toggle(progress: Float): ToggleFrame {
        val p = progress.coerceIn(0f, 1f)
        fun smooth(t: Float): Float {
            val x = t.coerceIn(0f, 1f)
            return x * x * (3f - 2f * x)
        }
        fun segment(start: Float, end: Float, from: Float, to: Float): Float =
            from + (to - from) * smooth((p - start) / (end - start))
        val scale = when {
            p < .38f -> segment(0f, .38f, 1f, .2f)
            p < .72f -> segment(.38f, .72f, .2f, 1.12f)
            p < .86f -> segment(.72f, .86f, 1.12f, .97f)
            else -> segment(.86f, 1f, .97f, 1f)
        }
        return ToggleFrame(scale, smooth((p - .28f) / .20f))
    }

    fun triangles(progress: Float, previous: Boolean): List<SkipTriangle> {
        val p = progress.coerceIn(0f, 1f)
        val forward = listOf(
            SkipTriangle(9f, p, p),
            SkipTriangle(9f + 15f * p, 1f, 1f),
            SkipTriangle(24f + 15f * p, 1f, 1f - p),
        )
        return if (previous) forward.map { it.copy(left = 48f - it.left - 15f) } else forward
    }
}
