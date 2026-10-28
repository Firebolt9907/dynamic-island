package com.island.dynamicisland.ui.island

/** Exchange faces edge-on so artwork is never shown backwards. */
internal object ArtworkFlipMotion {
    fun showsNewArtwork(progress: Float) = progress >= .5f
    fun rotationY(progress: Float): Float {
        val p = progress.coerceIn(0f, 1f)
        return if (p < .5f) -180f * p else 180f * (1f - p)
    }
}
