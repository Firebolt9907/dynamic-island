package com.island.dynamicisland.ui.island

import kotlin.math.min

/** A rectangle whose corners remain inside the bubble's circular corner arcs. */
internal object IslandContentBounds {
    fun inset(width: Float, height: Float, radius: Float): Float =
        min(radius, min(width, height) / 2f).coerceAtLeast(0f) * (1f - 0.70710677f) +
            min(.75f, min(width, height) * .01f) // Keep integer placement rounding inside the arc.

    fun scale(width: Float, height: Float, radius: Float, contentWidth: Float,
              contentHeight: Float, padding: Float): Float {
        val inset = inset(width, height, radius)
        return min(1f, min((width - 2f * inset) / (contentWidth - 2f * padding).coerceAtLeast(1f),
            (height - 2f * inset) / (contentHeight - 2f * padding).coerceAtLeast(1f))).coerceAtLeast(0f)
    }

    fun position(desired: Float, extent: Float, size: Float, inset: Float): Float =
        desired.coerceIn(inset, (extent - inset - size).coerceAtLeast(inset))
}

