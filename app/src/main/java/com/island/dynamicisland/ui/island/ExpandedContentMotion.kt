package com.island.dynamicisland.ui.island

/** Independent reveal windows for title, artist, timeline, and transport controls, and lyrics. */
internal object ExpandedContentMotion {
    private val starts = floatArrayOf(0.08f, 0.10f, 0.14f, 0.18f, 0.24f)
    private val ends = floatArrayOf(0.78f, 0.80f, 0.86f, 0.90f, 0.96f)

    fun blurRadiusDp(expansion: Float): Float {
        val p = expansion.coerceIn(0f, 1f)
        val delayed = p * p
        return 12f * (1f - delayed * delayed * (3f - 2f * delayed))
    }

    fun translationYDp(expansion: Float, centerY: Float, elementHeight: Float,
                       compactHeight: Float, topInset: Float = 12f): Float {
        val travel = (compactHeight / 2f - centerY) * (1f - IslandGrowth.heightFraction(expansion))
        // Each element travels independently; stop its upper edge inside the content envelope.
        return maxOf(travel, topInset - centerY + elementHeight / 2f).coerceAtMost(0f)
    }

    /** Conservative lower edge after independent row travel, including the outer padding. */
    fun envelopeHeight(expansion: Float, fullHeight: Float, maxRowHeight: Float, compactHeight: Float): Float {
        val growth = IslandGrowth.heightFraction(expansion)
        val row = maxRowHeight.coerceAtMost((fullHeight - 24f).coerceAtLeast(0f))
        val bottom = (fullHeight - 12f) * growth +
            (compactHeight / 2f + row / 2f) * (1f - growth)
        return maxOf(bottom, 12f + row) + 12f
    }

    fun fraction(expansion: Float, order: Int): Float {
        val index = order.coerceIn(starts.indices)
        val t = ((expansion - starts[index]) / (ends[index] - starts[index])).coerceIn(0f, 1f)
        // Smooth velocity at both ends; collapse follows the same path in reverse.
        return t * t * (3f - 2f * t)
    }
}
