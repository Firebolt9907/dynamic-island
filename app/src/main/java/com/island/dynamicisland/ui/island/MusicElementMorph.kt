package com.island.dynamicisland.ui.island

/** Positions in dp, relative to the continuously morphing pill's top-left corner. */
internal data class MusicElementPosition(val left: Float, val top: Float)

internal data class SharedMusicPlacement(val artLeft: Float, val artTop: Float,
    val barsLeft: Float, val barsTop: Float)

internal object MusicElementMorph {
    /** A small downward bow while shared icons travel; endpoints stay exactly aligned. */
    fun downwardArcDp(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val bow = 4f * p * (1f - p)
        return 8f * bow * bow
    }

    /** Interpolate in screen space, then cancel the moving card's origin exactly once. */
    fun travellingElementLeft(compactScreenLeft: Float, expandedScreenLeft: Float,
                              cardScreenLeft: Float, progress: Float): Float =
        mix(compactScreenLeft, expandedScreenLeft, progress) - cardScreenLeft

    /** Start at the secondary's actual bars, then travel to the expanded header slot. */
    fun promotedBarsLeft(secondaryWidth: Float, height: Float, targetLeftPx: Float,
                         progress: Float, density: Float, indicatorWidth: Float = height * CompactVisualizerWidthFraction): Float =
        mix((secondaryWidth - height * (.2f + CompactVisualizerTrailingInset) - indicatorWidth) * density, targetLeftPx, progress)

    /** Match visible edge gaps, accounting for the artwork's internal transparent inset. */
    fun symmetricPlacement(width: Float, height: Float, safeInset: Float, edgeGap: Float,
        centerY: Float, artSlot: Float, artInset: Float, barsWidth: Float, barsHeight: Float,
        artLead: Float = 0f, barsTrail: Float = 0f): SharedMusicPlacement {
        val margin = maxOf(edgeGap, safeInset + artInset)
        val halfHeight = maxOf(artSlot, barsHeight) / 2f
        val center = centerY.coerceIn(safeInset + halfHeight,
            (height - safeInset - halfHeight).coerceAtLeast(safeInset + halfHeight))
        return SharedMusicPlacement(margin - artInset + artLead, center - artSlot / 2f,
            width - margin - barsWidth - barsTrail, center - barsHeight / 2f)
    }

    /** Shared elements arrive at the same scale as the expanded header's reserved slots. */
    fun sharedSize(compact: Float, expanded: Float, contentScale: Float, progress: Float): Float =
        mix(compact, maxOf(minOf(compact, expanded), expanded * contentScale),
            progress.coerceIn(0f, 1f).let { it + .45f * it * (1f - it) })

    fun artworkSlotSize(compact: Float, header: Float, contentScale: Float, progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        val inset = p * (10f / header).coerceAtMost(.2f)
        return sharedSize(compact, header - 20f, contentScale, p) / (1f - 2f * inset)
    }

    private fun mix(start: Float, end: Float, progress: Float): Float =
        start + (end - start) * progress.coerceIn(0f, 1f)

    fun art(compactHeight: Float, contentTop: Float, progress: Float, headerHeight: Float = 54f, artSize: Float = 54f) = MusicElementPosition(
        left = mix(compactHeight * 0.20f, 12f, progress),
        top = mix(compactHeight * 0.20f, contentTop + (headerHeight - artSize) / 2f, progress),
    )

    fun visualizer(compactWidth: Float, compactHeight: Float, expandedWidth: Float,
        contentTop: Float, progress: Float, headerHeight: Float = 54f) = MusicElementPosition(
        left = mix(compactWidth - compactHeight * (0.20f + CompactVisualizerTrailingInset + CompactVisualizerWidthFraction), expandedWidth - 12f - 24f, progress),
        top = mix(compactHeight * (1f - CompactVisualizerHeightFraction) / 2f, contentTop + (headerHeight - 20f) / 2f, progress),
    )
}
