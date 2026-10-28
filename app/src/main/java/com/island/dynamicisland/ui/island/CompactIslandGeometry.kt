package com.island.dynamicisland.ui.island

internal object CompactIslandGeometry {
    /** Keep equal outer insets and a centered pill; shrink until the tighter side reaches the cutout. */
    fun width(
        height: Float,
        cameraRadius: Float,
        cameraOffset: Float,
        indicatorWidth: Float,
        grouped: Boolean = false,
        leadingWidth: Float = height * .6f,
    ): Float {
        val padding = height * .2f
        if (grouped) return maxOf(height,
            cameraRadius * 2f + leadingWidth + 6f + indicatorWidth + padding)
        return maxOf(height, 2f * maxOf(padding + leadingWidth + cameraRadius - cameraOffset,
            padding + indicatorWidth + cameraRadius + cameraOffset))
    }

    fun centerFromCameraLeft(width: Float, cameraRadius: Float, cameraOffset: Float): Float =
        cameraOffset - cameraRadius + width / 2f
}

/** Put spare room on the camera side of a grouped pill, never between its teams. */
internal fun sportsCameraSideSpace(width: Float, edge: Float, teamsWidth: Float, expansion: Float): Float =
    (width - 2f * edge - teamsWidth - 6f).coerceAtLeast(0f) * (1f - expansion.coerceIn(0f, 1f))

