package com.island.dynamicisland.ui.island

internal data class SkipTriangle(val left: Float, val scale: Float, val alpha: Float)

/** A replacement grows at the back while the two original triangles advance one slot. */
internal object PlaybackControlMotion {
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
